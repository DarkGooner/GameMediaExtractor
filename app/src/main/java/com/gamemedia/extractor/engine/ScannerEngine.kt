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

    // Exact (extensionless or fixed-name) Unity data files that never carry a recognizable
    // extension: "level0", "level1", ... (scene data), "globalgamemanagers", "mainData"
    // (legacy WebPlayer builds), and "resources.resource".
    private val unityFixedNames = setOf("globalgamemanagers", "maindata")

    // Known-irrelevant extensions we should never queue for Unity carving, even inside a
    // recognized game/Data/StreamingAssets folder - these are runtime/mono/config files that
    // real-world IL2CPP builds are full of (see il2cpp_data/etc/mono/**) and scanning them
    // would just waste time and memory.
    private val nonBundleExtensions = setOf(
        "dll", "exe", "pdb", "so", "dylib", "txt", "xml", "json", "ini", "log", "crc",
        "cfg", "bat", "sh", "py", "js", "html", "css", "md", "ttf", "otf", "url",
        "manifest", "meta", "info", "config", "aspx", "browser", "map", "gc"
    )

    fun scan(root: DocumentFile): Flow<DetectedMedia> = flow {
        val detectedEngine = detectEngine(root)
        walk(root, detectedEngine, this)
    }.flowOn(Dispatchers.IO)

    private fun detectEngine(root: DocumentFile): EngineType {
        var hasRpgAssets = false
        var hasUnityAssets = false
        fun probe(dir: DocumentFile, depth: Int) {
            if (depth > 4) return
            for (child in dir.listFiles()) {
                val name = child.name.orEmpty()
                if (child.isDirectory) {
                    // A "<GameName>_Data" (or plain "Data") folder is the single strongest
                    // signal of a Unity build - present in every standard Unity player layout.
                    if (name.endsWith("_Data", ignoreCase = true) || name.equals("Data", ignoreCase = true)) {
                        hasUnityAssets = true
                    }
                    probe(child, depth + 1)
                } else {
                    if (RpgMakerExtractor.isEncryptedAsset(name) || name.equals("Game.rgss3a", true)) {
                        hasRpgAssets = true
                    }
                    if (isUnityMarkerFile(name) || isUnityBundleCandidate(name)) {
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

    /** Files that unambiguously signal "this is a Unity player build", regardless of engine data. */
    private fun isUnityMarkerFile(name: String): Boolean =
        name.equals("UnityPlayer.dll", true) ||
        name.equals("GameAssembly.dll", true) ||
        name.equals("app.info", true) ||
        name.equals("boot.config", true) ||
        name.startsWith("UnityCrashHandler", true)

    /**
     * Broad match for "this file is plausibly a Unity asset container" - covers standard
     * extensions (.assets/.bundle/.unity3d/.resource/.resS) as well as the many real-world
     * cases where Unity ships bundles with no extension at all or an arbitrary custom name
     * (e.g. "level0", "globalgamemanagers", "main_game", "client.dat" in StreamingAssets).
     */
    private fun isUnityBundleCandidate(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        val lower = name.lowercase()
        return ext in unityBundleExtensions ||
            lower in unityFixedNames ||
            lower.startsWith("level") ||
            lower.startsWith("sharedassets")
    }

    /**
     * Looser catch-all used only for files sitting inside a StreamingAssets folder: these are
     * very commonly custom-named Addressables/AssetBundles (see e.g. "main_game", "thumbs",
     * "release_v_1_2_3", "client.dat" in a real StreamingAssets tree) with no naming convention
     * at all. Rather than trying to enumerate every possible name, treat anything that isn't
     * obviously a non-bundle runtime/text/config file as a carving candidate.
     */
    private fun isStreamingAssetsCandidate(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return true // extensionless custom bundle names are the common case
        return ext !in nonBundleExtensions
    }

    private suspend fun walk(
        dir: DocumentFile,
        engine: EngineType,
        collector: kotlinx.coroutines.flow.FlowCollector<DetectedMedia>,
        inStreamingAssets: Boolean = false
    ) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                val childInStreaming = inStreamingAssets || child.name.equals("StreamingAssets", ignoreCase = true)
                walk(child, engine, collector, childInStreaming)
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
                engine == EngineType.UNITY && isUnityBundleCandidate(name) -> {
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
                engine == EngineType.UNITY && inStreamingAssets && isStreamingAssetsCandidate(name) -> {
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
