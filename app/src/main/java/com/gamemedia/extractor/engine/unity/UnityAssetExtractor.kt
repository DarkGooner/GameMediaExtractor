package com.gamemedia.extractor.engine.unity

import com.gamemedia.extractor.data.model.MediaType
import java.io.OutputStream

/**
 * Signature-based media "carver" used to locate and extract embedded images/video/audio
 * from decompressed Unity asset data (either a decompressed UnityFS bundle, or a raw
 * `.assets`/`.resource`/`resS` file, which are frequently stored uncompressed on disk).
 *
 * Rationale: Unity's serialized object graph for Texture2D/VideoClip/Sprite assets differs
 * across engine versions and requires per-version type trees to walk generically. Carving
 * by file-format magic numbers + light structural validation is version-agnostic and is a
 * long-standing, widely used technique for this kind of bulk media recovery.
 */
object UnityAssetExtractor {

    data class Candidate(val type: MediaType, val startOffset: Long, val length: Long, val extension: String)

    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val JPEG_SIG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val WEBP_RIFF = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
    private val OGG_SIG = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    private val WEBM_EBML = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val MP4_FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())

    /**
     * Scans [data] for embedded media and returns candidate byte ranges. This performs a
     * single linear pass so it stays efficient even on multi-hundred-MB decompressed
     * buffers; callers should chunk very large bundles upstream if memory pressure is a
     * concern (see [UnityFsReader] notes).
     */
    fun findCandidates(data: ByteArray): List<Candidate> {
        val results = mutableListOf<Candidate>()
        var i = 0
        val n = data.size
        while (i < n - 8) {
            when {
                matches(data, i, PNG_SIG) -> {
                    val end = findPngEnd(data, i)
                    if (end > i) {
                        results += Candidate(MediaType.IMAGE, i.toLong(), (end - i).toLong(), ".png")
                        i = end
                        continue
                    }
                }
                matches(data, i, JPEG_SIG) -> {
                    val end = findJpegEnd(data, i)
                    if (end > i) {
                        results += Candidate(MediaType.IMAGE, i.toLong(), (end - i).toLong(), ".jpg")
                        i = end
                        continue
                    }
                }
                matches(data, i, WEBP_RIFF) && i + 12 <= n &&
                    data[i + 8] == 'W'.code.toByte() && data[i + 9] == 'E'.code.toByte() -> {
                    val size = readInt32LE(data, i + 4) + 8
                    if (size in 12..(n - i)) {
                        results += Candidate(MediaType.IMAGE, i.toLong(), size.toLong(), ".webp")
                        i += size
                        continue
                    }
                }
                matches(data, i, OGG_SIG) -> {
                    val end = findOggEnd(data, i)
                    if (end > i) {
                        results += Candidate(MediaType.AUDIO, i.toLong(), (end - i).toLong(), ".ogg")
                        i = end
                        continue
                    }
                }
                matches(data, i, WEBM_EBML) -> {
                    // WebM/Matroska has no simple end marker; bound by next signature hit
                    // or a generous cap, whichever comes first, to avoid unbounded scans.
                    val cap = minOf(n, i + 64 * 1024 * 1024)
                    results += Candidate(MediaType.VIDEO, i.toLong(), (cap - i).toLong(), ".webm")
                    i = cap
                    continue
                }
                i + 8 <= n && matches(data, i + 4, MP4_FTYP) -> {
                    val boxSize = readInt32BE(data, i)
                    val start = i
                    val cap = if (boxSize in 8..(n - i)) findMp4End(data, start) else minOf(n, start + 64 * 1024 * 1024)
                    results += Candidate(MediaType.VIDEO, start.toLong(), (cap - start).toLong(), ".mp4")
                    i = cap
                    continue
                }
            }
            i++
        }
        return results
    }

    /** Streams the bytes for a single [Candidate] out to [output] in fixed-size chunks. */
    fun writeCandidate(data: ByteArray, candidate: Candidate, output: OutputStream) {
        val start = candidate.startOffset.toInt()
        val len = candidate.length.toInt()
        val chunkSize = 1 shl 16
        var written = 0
        while (written < len) {
            val n = minOf(chunkSize, len - written)
            output.write(data, start + written, n)
            written += n
        }
    }

    private fun matches(data: ByteArray, offset: Int, sig: ByteArray): Boolean {
        if (offset + sig.size > data.size) return false
        for (k in sig.indices) if (data[offset + k] != sig[k]) return false
        return true
    }

    private fun findPngEnd(data: ByteArray, start: Int): Int {
        // PNG IEND chunk: 4-byte len(=0) + "IEND" + 4-byte CRC
        val iend = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
        var i = start + 8
        while (i + 8 <= data.size) {
            if (matches(data, i + 4, iend)) return i + 12
            val len = readInt32BE(data, i)
            i += 8 + len + 4
            if (len < 0 || i > data.size) break
        }
        return -1
    }

    private fun findJpegEnd(data: ByteArray, start: Int): Int {
        var i = start + 2
        while (i + 4 <= data.size) {
            if ((data[i].toInt() and 0xFF) == 0xFF && (data[i + 1].toInt() and 0xFF) == 0xD9) return i + 2
            i++
        }
        return -1
    }

    private fun findOggEnd(data: ByteArray, start: Int): Int {
        // Scan forward for the next "OggS" page header after this one, or cap the search.
        var i = start + 4
        val cap = minOf(data.size, start + 32 * 1024 * 1024)
        while (i + 4 < cap) {
            if (matches(data, i, OGG_SIG)) return i
            i++
        }
        return cap
    }

    private fun findMp4End(data: ByteArray, start: Int): Int {
        var i = start - 4 // back up to the box size field before "ftyp"
        var total = 0
        val cap = minOf(data.size, start + 256 * 1024 * 1024)
        while (i + 8 < cap) {
            val boxSize = readInt32BE(data, i)
            if (boxSize <= 0) break
            total += boxSize
            i += boxSize
            if (i >= cap) break
        }
        return minOf(cap, start - 4 + total).coerceAtLeast(start)
    }

    private fun readInt32BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun readInt32LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}
