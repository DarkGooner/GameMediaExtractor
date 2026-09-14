package com.gamemedia.extractor.engine.unity

import net.jpountz.lz4.LZ4Factory
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream

/**
 * Minimal reader for Unity's "UnityFS" AssetBundle container format.
 *
 * This does NOT implement full Unity serialized-object / type-tree deserialization
 * (that is the approach tools like AssetStudio use, and it requires per-Unity-version
 * type trees plus a large amount of reflection-like parsing). Instead, this reader:
 *
 *  1. Parses the UnityFS header and "blocks & directory" info.
 *  2. Decompresses each storage block (supports the common LZ4/LZ4HC and LZMA
 *     compression flags; blocks stored uncompressed are also supported).
 *  3. Hands the resulting raw byte stream back to the caller, who then uses signature
 *     based carving ([UnityAssetExtractor]) to locate embedded PNG/JPEG/WebP images and
 *     MP4/WebM/OGG video/audio streams inside the decompressed asset data.
 *
 * This heuristic-carving approach is intentionally chosen over full type-tree parsing:
 * it is engine/version agnostic and works across the wide range of Unity versions found
 * in the wild, at the cost of not recovering original per-asset filenames for bundles
 * that don't store them in a readily accessible form.
 */
class UnityFsReader {

    data class BlockInfo(val compressedSize: Int, val uncompressedSize: Int, val flags: Int)

    /** Returns true if [header] bytes look like the start of a UnityFS bundle. */
    fun isUnityFsBundle(header: ByteArray): Boolean {
        val magic = "UnityFS"
        if (header.size < magic.length) return false
        return String(header, 0, magic.length, Charsets.US_ASCII) == magic
    }

    /**
     * Reads and fully decompresses a UnityFS bundle from [input] into memory.
     *
     * NOTE: bundles are decompressed as a whole here for simplicity of block/dir parsing;
     * for very large bundles the caller should prefer scanning the underlying .assets /
     * resource files directly when present alongside the bundle (these are frequently
     * already-uncompressed and can be signature-scanned via streaming without this step).
     */
    fun decompressBundle(input: InputStream, maxBundleBytes: Long): ByteArray? {
        val data = DataInputStream(input)
        val signature = readCString(data)
        if (signature != "UnityFS") return null

        val fileVersion = data.readInt()
        val unityVersion = readCString(data)
        val unityRevision = readCString(data)
        val bundleSize = data.readLong()
        if (bundleSize > maxBundleBytes) return null // guard against absurd/malformed sizes

        val compressedBlocksInfoSize = data.readInt()
        val uncompressedBlocksInfoSize = data.readInt()
        val flags = data.readInt()

        // Blocks info may itself be compressed (flags & 0x3F -> compression type).
        val blocksInfoCompression = flags and 0x3F
        val blocksInfoRaw = ByteArray(compressedBlocksInfoSize)
        readFully(data, blocksInfoRaw)
        val blocksInfoDecompressed = decompressBlock(
            blocksInfoRaw, blocksInfoCompression, uncompressedBlocksInfoSize
        ) ?: return null

        val biStream = DataInputStream(ByteArrayInputStream(blocksInfoDecompressed))
        biStream.skipBytes(16) // uncompressed data hash (128-bit)
        val blockCount = biStream.readInt()
        val blocks = ArrayList<BlockInfo>(blockCount)
        repeat(blockCount) {
            val uncompressedSize = biStream.readInt()
            val compressedSize = biStream.readInt()
            val blockFlags = biStream.readShort().toInt()
            blocks += BlockInfo(compressedSize, uncompressedSize, blockFlags)
        }
        // Directory info (per-asset names/offsets) follows; skipped intentionally since we
        // rely on signature carving rather than per-asset metadata.

        val output = ByteArrayOutputStream()
        for (block in blocks) {
            val compressed = ByteArray(block.compressedSize)
            readFully(data, compressed)
            val decompressed = decompressBlock(compressed, block.flags and 0x3F, block.uncompressedSize)
                ?: continue
            output.write(decompressed)
        }
        return output.toByteArray()
    }

    private fun decompressBlock(input: ByteArray, compressionType: Int, uncompressedSize: Int): ByteArray? {
        return try {
            when (compressionType) {
                0 -> input // none
                1 -> { // LZMA
                    LZMAInputStream(ByteArrayInputStream(input), input.size.toLong()).use { it.readBytes() }
                }
                2, 3 -> { // LZ4 / LZ4HC
                    val decompressor = LZ4Factory.fastestInstance().safeDecompressor()
                    val out = ByteArray(uncompressedSize)
                    decompressor.decompress(input, 0, out, 0, uncompressedSize)
                    out
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readCString(data: DataInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = data.readByte()
            if (b.toInt() == 0) break
            sb.append(b.toInt().toChar())
        }
        return sb.toString()
    }

    private fun readFully(data: DataInputStream, dest: ByteArray) {
        var off = 0
        while (off < dest.size) {
            val r = data.read(dest, off, dest.size - off)
            if (r < 0) throw java.io.EOFException()
            off += r
        }
    }
}
