# Game Media Extractor

An Android app that scans Unity and RPG Maker (MV/MZ/VX Ace) game folders and extracts
their images and video into a folder you choose, with background extraction, a live
progress/ETA UI, and extraction history.

## Project overview

- **UI**: Jetpack Compose, Material 3 (Material You dynamic color on Android 12+), dark/light/system theme.
- **Architecture**: MVVM + Repository, Hilt for DI, Kotlin Coroutines + StateFlow throughout.
- **Background work**: `WorkManager` (`ExtractionWorker`) promoted to a foreground service via `setForeground`, with a persistent progress notification, pause/resume, and cancellation.
- **Storage**: Scoped storage / SAF (`OpenDocumentTree`) for both the source game folder and destination — no broad storage permissions required.
- **History**: Room database of past extraction jobs.

## Supported formats

### RPG Maker MV / MZ — fully implemented
- Detects and decrypts `.rpgmvp`/`.png_` (images), `.rpgmvo`/`.ogg_` (audio), `.rpgmvm`/`.m4a_`, `.rpgmvv`/`.mp4_` (video).
- Reads the project's shared 16-byte encryption key from `System.json` (`encryptionKey` field), then streams each file, XOR-decrypting only the first 16 bytes and copying the rest — so even multi-hundred-MB video files decrypt with a flat, tiny memory footprint.
- Also unpacks unencrypted images/video/audio already sitting in the project (they're just copied through).

### RPG Maker VX Ace — partial
- `.rgss3a` archive directory parsing (XOR-seeded header/filename/content obfuscation) is implemented in `RpgMakerExtractor.readRgss3aDirectory`. Wiring this into the scan/extraction pipeline end-to-end is left as a follow-up (see Known limitations).

### Unity — heuristic, version-agnostic
Unity's real asset format requires per-engine-version "type trees" to walk the serialized object graph generically (this is what large dedicated tools like AssetStudio do, and it's thousands of lines of version-specific logic). Rather than embedding that, this app:

1. Parses the `UnityFS` AssetBundle container header and block/compression table.
2. Decompresses each block (supports **none / LZ4 / LZ4HC / LZMA**, the compression modes actually used by AssetBundles).
3. Signature-scans ("carves") the decompressed bytes for embedded **PNG, JPEG, WebP** images and **MP4, WebM, OGG** audio/video by their file-format magic numbers and structural end-markers.

This works well for pulling raw media out of bundles regardless of Unity version, at the cost of not recovering the *original* per-asset names Unity assigned internally (extracted files are named after their source bundle plus an index). Raw, already-uncompressed `.assets`/`.resource`/`.resS` files are scanned the same way without the UnityFS decompression step.

## How to build & run

1. Open the project root in **Android Studio (Koala or newer)** — the Gradle wrapper (including `gradle-wrapper.jar`, pinned to Gradle 8.7) is included, so it'll sync without any extra setup.
2. Alternatively from the command line: `./gradlew assembleDebug` (macOS/Linux) or `gradlew.bat assembleDebug` (Windows).
3. Run on a device/emulator running API 26+.
4. From the app: **Select Game Folder** → **Select Destination** → **Scan for Media** → **Extract**.

Minimum SDK 26, target/compile SDK 35, Kotlin 1.9.24, AGP 8.5.2.

## How the GitHub Actions CI works

`.github/workflows/android-build.yml`:
- Runs on every push/PR to `main`, and on `v*.*.*` tags.
- Sets up JDK 17 + Gradle (with dependency caching).
- Runs unit tests (`testDebugUnitTest`) and lint (`lintDebug`).
- On pushes to `main` and tags: builds a **signed release APK**; on tags, also builds a **signed AAB** and creates a **GitHub Release** with both artifacts attached.
- On pull requests: builds an unsigned **debug APK** instead (no secrets required), so external PRs can still be validated.

### Setting up signing secrets

The workflow expects these repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Your `.jks`/`.keystore` file, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias inside the keystore |
| `KEY_PASSWORD` | Key password |

If `KEYSTORE_BASE64` is unset, release builds simply produce an **unsigned** APK/AAB (Gradle skips applying the signing config when `KEYSTORE_PATH` isn't set) — useful for forks/CI runs without secrets configured.

To generate a new keystore:
```bash
keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias my-key-alias
```

## Known limitations

- **Unity extraction is heuristic (signature-carving), not full type-tree deserialization.** Original in-engine asset names for Unity textures/clips are not recovered; files are numbered per source bundle. Extremely large or already-compressed-elsewhere bundles are capped at 512MB for in-memory decompression as a safety guard — bundles bigger than that are skipped rather than risking an OOM.
- **RPG Maker VX Ace (`.rgss3a`) archive extraction** has its container-format parser implemented but isn't yet wired into the main scan/extract pipeline end-to-end — MV/MZ is the fully wired path.
- **No native (JNI/C++) code** is included; all hot paths (RPGM XOR streaming, Unity block decompression, media carving) are implemented in Kotlin using chunked I/O and the `lz4-java`/`xz` Java libraries. This keeps the codebase portable and easy to build in CI without an NDK toolchain, at some performance cost versus hand-tuned native code on very large batches.
- Video thumbnail generation and image thumbnails in the scan list are wired for Coil but not exhaustively tested against every codec/container combination that might appear in the wild.
- This tool is intended for extracting assets from games you own, for backup, modding, translation, or archival purposes. Respect the license terms of any game you use it with.
