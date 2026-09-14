package com.gamemedia.extractor.engine

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.gamemedia.extractor.data.model.DetectedMedia
import com.gamemedia.extractor.data.model.EngineType
import com.gamemedia.extractor.data.model.MediaType
import com.gamemedia.extractor.engine.rpgm.RpgMakerExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.UUID

/**
 * Walks a user-selected SAF tree, detects which engine(s) produced the game, and yields
 * [DetectedMedia] entries as it finds them. Emits incrementally via Flow so the UI can show
 * scan progress without waiting for the whole (potentially multi-GB) tree to finish.
 */
class ScannerEngine(private val context: Context) {

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp")
    private val videoExtensions = setOf("mp4", "webm", "mov")
    private val unityBundleExtensions = setOf("unity3d", "bundle", "assets", "resource", "resS")

    fun scan(root: DocumentFile): Flow<DetectedMedia> = flow {
        val detectedEngine = detectEngine(root)
        walk(root, detectedEngine, this)
    }.flowOn(Dispatchers.IO)

    private fun detectEngine(root: DocumentFile): EngineType {
        var hasRpgAssets = false
        var hasUnityAssets = false
        fun probe(dir: DocumentFile, depth: Int) {
            if (depth > 3) return
            for (child in dir.listFiles()) {
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    probe(child, depth + 1)
                } else {
                    if (RpgMakerExtractor.isEncryptedAsset(name) || name.equals("Game.rgss3a", true)) {
                        hasRpgAssets = true
                    }
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext in unityBundleExtensions || name == "globalgamemanagers") {
                        hasUnityAssets = true
                    }
                }
            }
        }
        probe(root, 0)
        return when {
            hasRpgAssets -> EngineType.RPG_MAKER_MV_MZ
            hasUnityAssets -> EngineType.UNITY
            else -> EngineType.UNKNOWN
        }
    }

    private suspend fun walk(
        dir: DocumentFile,
        engine: EngineType,
        collector: kotlinx.coroutines.flow.FlowCollector<DetectedMedia>
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                walk(child, engine, collector)
                continue
            }
            val name = child.name.orEmpty()
            val ext = name.substringAfterLast('.', "").lowercase()

            when {
                RpgMakerExtractor.isEncryptedAsset(name) -> {
                    collector.emit(
                        DetectedMedia(
                            id = UUID.randomUUID().toString(),
                            sourceUri = child.uri,
                            sourcePathLabel = name,
                            displayName = name.substringBeforeLast('.') + RpgMakerExtractor.realExtensionFor(name),
                            engine = EngineType.RPG_MAKER_MV_MZ,
                            type = classifyRpgm(name),
                            estimatedSizeBytes = child.length(),
                            isEncrypted = true
                        )
                    )
                }
                ext in imageExtensions && !RpgMakerExtractor.isEncryptedAsset(name) -> {
                    collector.emit(
                        DetectedMedia(
                            id = UUID.randomUUID().toString(),
                            sourceUri = child.uri,
                            sourcePathLabel = name,
                            displayName = name,
                            engine = engine,
                            type = MediaType.IMAGE,
                            estimatedSizeBytes = child.length(),
                            isEncrypted = false
                        )
                    )
                }
                ext in videoExtensions -> {
                    collector.emit(
                        DetectedMedia(
                            id = UUID.randomUUID().toString(),
                            sourceUri = child.uri,
                            sourcePathLabel = name,
                            displayName = name,
                            engine = engine,
                            type = MediaType.VIDEO,
                            estimatedSizeBytes = child.length(),
                            isEncrypted = false
                        )
                    )
                }
                ext in unityBundleExtensions -> {
                    // The bundle itself is the unit of work; the worker will carve media out
                    // of it during extraction rather than during the (fast) scan pass.
                    collector.emit(
                        DetectedMedia(
                            id = UUID.randomUUID().toString(),
                            sourceUri = child.uri,
                            sourcePathLabel = name,
                            displayName = name,
                            engine = EngineType.UNITY,
                            type = MediaType.UNKNOWN,
                            estimatedSizeBytes = child.length(),
                            isEncrypted = false
                        )
                    )
                }
            }
        }
    }

    private fun classifyRpgm(name: String): MediaType {
        val realExt = RpgMakerExtractor.realExtensionFor(name)
        return when (realExt) {
            ".png" -> MediaType.IMAGE
            ".mp4" -> MediaType.VIDEO
            ".ogg", ".m4a" -> MediaType.AUDIO
            else -> MediaType.UNKNOWN
        }
    }
}
