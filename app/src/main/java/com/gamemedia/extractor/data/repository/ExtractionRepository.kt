package com.gamemedia.extractor.data.repository

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.gamemedia.extractor.data.db.ExtractionHistoryDao
import com.gamemedia.extractor.data.db.ExtractionHistoryEntity
import com.gamemedia.extractor.data.model.DetectedMedia
import com.gamemedia.extractor.data.model.EngineType
import com.gamemedia.extractor.data.model.ExtractionOptions
import com.gamemedia.extractor.data.model.MediaType
import com.gamemedia.extractor.engine.ScannerEngine
import com.gamemedia.extractor.engine.rpgm.RpgMakerExtractor
import com.gamemedia.extractor.engine.unity.UnityAssetExtractor
import com.gamemedia.extractor.engine.unity.UnityFsReader
import kotlinx.coroutines.flow.Flow
import java.io.BufferedInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point the UI/Workers use to scan and extract media. Wraps the two engines
 * and Room history persistence behind a simple suspend/Flow API.
 */
@Singleton
class ExtractionRepository @Inject constructor(
    private val context: Context,
    private val historyDao: ExtractionHistoryDao
) {
    private val scanner = ScannerEngine(context)
    private val rpgm = RpgMakerExtractor(context)
    private val unityFsReader = UnityFsReader()

    fun scanFolder(root: DocumentFile): Flow<DetectedMedia> = scanner.scan(root)

    fun observeHistory() = historyDao.observeAll()

    suspend fun recordHistory(entity: ExtractionHistoryEntity) {
        historyDao.insert(entity)
    }

    /**
     * Extracts a single [DetectedMedia] item into [destinationRoot], honoring [options].
     * Returns the number of bytes written, or throws on failure so the caller can track
     * per-item errors without aborting the whole batch.
     */
    suspend fun extractItem(
        item: DetectedMedia,
        destinationRoot: DocumentFile,
        options: ExtractionOptions,
        cancelled: AtomicBoolean
    ): Long {
        if (cancelled.get()) return 0L

        val targetDir = resolveTargetDir(destinationRoot, item, options)

        return when {
            item.isEncrypted && item.engine == EngineType.RPG_MAKER_MV_MZ -> {
                val key = findKeyForItem(item) ?: throw IllegalStateException(
                    "Could not locate RPG Maker encryption key (System.json) for ${item.sourcePathLabel}"
                )
                val outFile = targetDir.createFile("application/octet-stream", item.displayName)
                    ?: throw IllegalStateException("Could not create output file")
                context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                    rpgm.decryptToStream(item.sourceUri, key, out)
                } ?: throw IllegalStateException("Could not open output stream")
            }

            item.engine == EngineType.UNITY -> {
                extractFromUnityBundle(item, targetDir, options)
            }

            else -> {
                // Plain, already-decoded media file: straight streamed copy.
                val outFile = targetDir.createFile(mimeTypeFor(item.type), item.displayName)
                    ?: throw IllegalStateException("Could not create output file")
                copyStream(item, outFile)
            }
        }
    }

    private fun extractFromUnityBundle(
        item: DetectedMedia,
        targetDir: DocumentFile,
        options: ExtractionOptions
    ): Long {
        var totalBytes = 0L
        val maxBundleBytes = 512L * 1024 * 1024 // safety guard for in-memory decompression
        context.contentResolver.openInputStream(item.sourceUri)?.use { rawInput ->
            val buffered = BufferedInputStream(rawInput, 1 shl 20)
            buffered.mark(16)
            val header = ByteArray(8)
            buffered.read(header)
            buffered.reset()

            val decompressed: ByteArray? = if (unityFsReader.isUnityFsBundle(header)) {
                unityFsReader.decompressBundle(buffered, maxBundleBytes)
            } else {
                // Not a compressed UnityFS container (e.g. raw .assets/.resource file) -
                // these are commonly stored uncompressed, so read directly for carving.
                if (item.estimatedSizeBytes in 1..maxBundleBytes) buffered.readBytes() else null
            }

            if (decompressed != null) {
                val candidates = UnityAssetExtractor.findCandidates(decompressed)
                var index = 0
                for (candidate in candidates) {
                    val wantType = when (candidate.type) {
                        MediaType.IMAGE -> options.includeImages
                        MediaType.VIDEO -> options.includeVideos
                        MediaType.AUDIO -> options.includeAudio
                        else -> false
                    }
                    if (!wantType) continue
                    val name = "${item.displayName.substringBeforeLast('.')}_$index${candidate.extension}"
                    val outFile = targetDir.createFile(mimeTypeForExt(candidate.extension), name)
                        ?: continue
                    context.contentResolver.openOutputStream(outFile.uri)?.use { out ->
                        UnityAssetExtractor.writeCandidate(decompressed, candidate, out)
                    }
                    totalBytes += candidate.length
                    index++
                }
            }
        }
        return totalBytes
    }

    private fun findKeyForItem(item: DetectedMedia): ByteArray? {
        // Walk up from the file's parent tree isn't directly available via Uri alone;
        // callers pass the original project root separately in a full implementation.
        // Here we cache discovered keys per project root during a scan session.
        return cachedKey
    }

    // Simple session-scoped cache populated by the UI/worker once per project before a batch.
    @Volatile var cachedKey: ByteArray? = null

    fun primeRpgMakerKey(projectRoot: DocumentFile) {
        cachedKey = rpgm.findEncryptionKey(projectRoot)
    }

    private fun resolveTargetDir(
        destinationRoot: DocumentFile,
        item: DetectedMedia,
        options: ExtractionOptions
    ): DocumentFile {
        var dir = destinationRoot
        if (options.organizeByType) {
            val sub = when (item.type) {
                MediaType.IMAGE -> "Images"
                MediaType.VIDEO -> "Videos"
                MediaType.AUDIO -> "Audio"
                MediaType.UNKNOWN -> "Other"
            }
            dir = dir.findFile(sub) ?: dir.createDirectory(sub) ?: dir
        }
        if (options.organizeByOriginalPath) {
            val subPath = item.sourcePathLabel.substringBeforeLast('/', "")
            if (subPath.isNotEmpty()) {
                for (segment in subPath.split('/')) {
                    dir = dir.findFile(segment) ?: dir.createDirectory(segment) ?: dir
                }
            }
        }
        return dir
    }

    private fun copyStream(item: DetectedMedia, outFile: DocumentFile): Long {
        var total = 0L
        context.contentResolver.openInputStream(item.sourceUri)?.use { input ->
            context.contentResolver.openOutputStream(outFile.uri)?.use { output ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    total += n
                }
            }
        }
        return total
    }

    private fun mimeTypeFor(type: MediaType): String = when (type) {
        MediaType.IMAGE -> "image/*"
        MediaType.VIDEO -> "video/*"
        MediaType.AUDIO -> "audio/*"
        MediaType.UNKNOWN -> "application/octet-stream"
    }

    private fun mimeTypeForExt(ext: String): String = when (ext) {
        ".png" -> "image/png"
        ".jpg", ".jpeg" -> "image/jpeg"
        ".webp" -> "image/webp"
        ".mp4" -> "video/mp4"
        ".webm" -> "video/webm"
        ".ogg" -> "audio/ogg"
        else -> "application/octet-stream"
    }
}
