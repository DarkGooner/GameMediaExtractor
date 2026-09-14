package com.gamemedia.extractor.data.model

import android.net.Uri

/** The game engine an input folder was detected as belonging to. */
enum class EngineType { UNITY, RPG_MAKER_MV_MZ, RPG_MAKER_VX_ACE, UNKNOWN }

/** Type of media asset that can be extracted. */
enum class MediaType { IMAGE, VIDEO, AUDIO, UNKNOWN }

/** A single candidate media item discovered while scanning a game folder. */
data class DetectedMedia(
    val id: String,
    val sourceUri: Uri,
    val sourcePathLabel: String,
    val displayName: String,
    val engine: EngineType,
    val type: MediaType,
    val estimatedSizeBytes: Long,
    val isEncrypted: Boolean,
    val containerOffset: Long = -1L,
    val containerLength: Long = -1L
)

/** Options that control how extraction is performed. */
data class ExtractionOptions(
    val overwriteExisting: Boolean = false,
    val organizeByType: Boolean = true,
    val organizeByOriginalPath: Boolean = false,
    val includeImages: Boolean = true,
    val includeVideos: Boolean = true,
    val includeAudio: Boolean = false,
    val decryptRpgMaker: Boolean = true
)

/** Immutable snapshot of extraction progress, emitted frequently via StateFlow. */
data class ExtractionProgress(
    val jobId: Long,
    val totalItems: Int,
    val processedItems: Int,
    val totalBytes: Long,
    val processedBytes: Long,
    val currentFileName: String,
    val bytesPerSecond: Double,
    val etaSeconds: Long,
    val isPaused: Boolean = false,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false,
    val failedItems: Int = 0,
    val errorMessage: String? = null
) {
    val percent: Float
        get() = if (totalBytes <= 0L) 0f else (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}
