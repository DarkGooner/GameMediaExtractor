package com.gamemedia.extractor.work

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gamemedia.extractor.data.db.ExtractionHistoryEntity
import com.gamemedia.extractor.data.model.DetectedMedia
import com.gamemedia.extractor.data.model.ExtractionOptions
import com.gamemedia.extractor.data.model.ExtractionProgress
import com.gamemedia.extractor.data.repository.ExtractionRepository
import com.gamemedia.extractor.service.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Runs the actual extraction batch as background work via WorkManager, promoted to a
 * foreground service (see [NotificationHelper]) so it survives Doze / background limits
 * on Android 12+. Supports cooperative cancellation and pause/resume through [controlFlags].
 */
@HiltWorker
class ExtractionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ExtractionRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_DEST_URI = "dest_uri"
        const val NOTIFICATION_ID = 4201

        // Shared, process-wide progress stream the UI observes while this worker runs.
        val progressFlow = MutableStateFlow<ExtractionProgress?>(null)
        val isPaused = AtomicBoolean(false)
        val isCancelled = AtomicBoolean(false)
        val pauseMutex = Mutex()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext, progress = 0, contentText = "Preparing extraction…"
        )
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())
        isCancelled.set(false)
        isPaused.set(false)

        val sourceUriStr = inputData.getString(KEY_SOURCE_URI) ?: return Result.failure()
        val destUriStr = inputData.getString(KEY_DEST_URI) ?: return Result.failure()

        val sourceRoot = DocumentFile.fromTreeUri(applicationContext, Uri.parse(sourceUriStr))
            ?: return Result.failure()
        val destRoot = DocumentFile.fromTreeUri(applicationContext, Uri.parse(destUriStr))
            ?: return Result.failure()

        repository.primeRpgMakerKey(sourceRoot)

        val items = mutableListOf<DetectedMedia>()
        repository.scanFolder(sourceRoot).collect { items += it }

        val options = ExtractionOptions()
        val totalBytes = max(items.sumOf { it.estimatedSizeBytes }, 1L)
        var processedBytes = 0L
        var processedItems = 0
        var failedItems = 0
        val startTime = System.currentTimeMillis()
        val jobId = startTime

        for (item in items) {
            // Cooperative pause: spin-wait on a suspend-friendly delay while paused.
            while (isPaused.get() && !isCancelled.get()) {
                kotlinx.coroutines.delay(300)
            }
            if (isCancelled.get()) break

            try {
                val written = repository.extractItem(item, destRoot, options, isCancelled)
                processedBytes += written
            } catch (e: Exception) {
                failedItems++
            }
            processedItems++

            val elapsedSec = max((System.currentTimeMillis() - startTime) / 1000.0, 0.001)
            val speed = processedBytes / elapsedSec
            val remainingBytes = (totalBytes - processedBytes).coerceAtLeast(0)
            val eta = if (speed > 0) (remainingBytes / speed).toLong() else -1L

            val progress = ExtractionProgress(
                jobId = jobId,
                totalItems = items.size,
                processedItems = processedItems,
                totalBytes = totalBytes,
                processedBytes = processedBytes,
                currentFileName = item.displayName,
                bytesPerSecond = speed,
                etaSeconds = eta,
                isPaused = isPaused.get(),
                failedItems = failedItems
            )
            progressFlow.value = progress

            setProgress(
                workDataOf(
                    "processed" to processedItems,
                    "total" to items.size,
                    "percent" to (progress.percent * 100).toInt()
                )
            )

            NotificationHelper.updateProgressNotification(
                applicationContext,
                percent = (progress.percent * 100).toInt(),
                contentText = "${item.displayName} • ${processedItems}/${items.size} • ETA ${formatEta(eta)}"
            )
        }

        val finalStatus = when {
            isCancelled.get() -> "CANCELLED"
            failedItems == items.size && items.isNotEmpty() -> "FAILED"
            else -> "COMPLETED"
        }

        repository.recordHistory(
            ExtractionHistoryEntity(
                gameFolderLabel = sourceRoot.name ?: sourceUriStr,
                destinationLabel = destRoot.name ?: destUriStr,
                engine = items.firstOrNull()?.engine?.name ?: "UNKNOWN",
                startedAtEpochMs = startTime,
                finishedAtEpochMs = System.currentTimeMillis(),
                totalItems = items.size,
                extractedItems = processedItems - failedItems,
                failedItems = failedItems,
                totalBytes = processedBytes,
                status = finalStatus
            )
        )

        progressFlow.value = progressFlow.value?.copy(
            isComplete = finalStatus == "COMPLETED",
            isCancelled = finalStatus == "CANCELLED"
        )

        NotificationHelper.finishNotification(applicationContext, finalStatus)

        return if (finalStatus == "FAILED") Result.failure() else Result.success()
    }

    private fun formatEta(seconds: Long): String {
        if (seconds < 0) return "…"
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }
}
