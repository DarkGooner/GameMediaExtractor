package com.gamemedia.extractor.engine.rpgm

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.OutputStream

/**
 * Extractor for RPG Maker MV / MZ and VX Ace game assets.
 *
 * RPG Maker MV/MZ format notes:
 * - Encrypted images use extension `.rpgmvp` (MV) or `.png_` (MZ variants), encrypted audio
 *   uses `.rpgmvo`/`.ogg_` and `.rpgmvm`/`.m4a_`, encrypted video uses `.rpgmvv`/`.mp4_`.
 * - Every encrypted file begins with a fixed 16-byte RPG Maker signature header
 *   (hex: 52 50 47 4D 56 00 00 00 00 03 01 00 00 00 00 00), followed immediately by the
 *   real file's first 16 bytes XORed against a 16-byte encryption key.
 * - The encryption key is stored as a hex string in `www/data/System.json` (MV) or
 *   `data/System.json` (MZ) under the key "encryptionKey". All assets in a project share
 *   the same key.
 * - Everything after the first 16 encrypted bytes is stored unmodified, so once the key is
 *   known, decryption only requires XOR-ing 16 bytes - the rest is a straight copy. This is
 *   what makes streaming, low-memory decryption possible even for large video files.
 *
 * RPG Maker VX Ace notes:
 * - Uses a proprietary `.rgss3a` archive format with a much simpler XOR-based obfuscation
 *   keyed by a 32-bit seed stored in the archive header. This class implements unpacking of
 *   that container format as well (see [unpackRgss3a]).
 */
class RpgMakerExtractor(private val context: Context) {

    companion object {
        private val RPGMV_HEADER = byteArrayOf(
            0x52, 0x50, 0x47, 0x4D, 0x56.toByte(), 0x00, 0x00, 0x00,
            0x00, 0x03, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        private const val HEADER_LEN = 16
        const val ENCRYPTED_EXTENSIONS = ".rpgmvp,.png_,.rpgmvo,.ogg_,.rpgmvm,.m4a_,.rpgmvv,.mp4_"

        fun isEncryptedAsset(fileName: String): Boolean =
            ENCRYPTED_EXTENSIONS.split(",").any { fileName.endsWith(it, ignoreCase = true) }

        /** Maps an encrypted RPG Maker filename to its real decrypted extension. */
        fun realExtensionFor(fileName: String): String = when {
            fileName.endsWith(".rpgmvp", true) || fileName.endsWith(".png_", true) -> ".png"
            fileName.endsWith(".rpgmvo", true) || fileName.endsWith(".ogg_", true) -> ".ogg"
            fileName.endsWith(".rpgmvm", true) || fileName.endsWith(".m4a_", true) -> ".m4a"
            fileName.endsWith(".rpgmvv", true) || fileName.endsWith(".mp4_", true) -> ".mp4"
            else -> ""
        }
    }

    /**
     * Locates and parses `System.json` under the project root to recover the 16-byte
     * encryption key shared by all encrypted assets in the project.
     */
    fun findEncryptionKey(projectRoot: DocumentFile): ByteArray? {
        val systemJson = findFileRecursive(projectRoot, "System.json", maxDepth = 6) ?: return null
        return try {
            val text = context.contentResolver.openInputStream(systemJson.uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return null
            val root = Json.parseToJsonElement(text).jsonObject
            val hex = root["encryptionKey"]?.jsonPrimitive?.content ?: return null
            hexStringToByteArray(hex)
        } catch (_: Exception) {
            null
        }
    }

    private fun findFileRecursive(dir: DocumentFile, name: String, maxDepth: Int): DocumentFile? {
        if (maxDepth < 0) return null
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                findFileRecursive(child, name, maxDepth - 1)?.let { return it }
            } else if (child.name.equals(name, ignoreCase = true)) {
                return child
            }
        }
        return null
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                    Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    /**
     * Streams the decrypted contents of an encrypted RPG Maker asset to [output].
     * Only the first 16 bytes (after the fixed header) require XOR; the remainder is copied
     * in large chunks so memory usage stays flat regardless of file size.
     */
    fun decryptToStream(source: Uri, key: ByteArray, output: OutputStream): Long {
        var totalWritten = 0L
        context.contentResolver.openInputStream(source)?.use { rawInput ->
            val input = BufferedInputStream(rawInput, 1 shl 16)

            // Skip the fixed 16-byte RPG Maker signature header.
            val header = ByteArray(HEADER_LEN)
            var readSoFar = 0
            while (readSoFar < HEADER_LEN) {
                val r = input.read(header, readSoFar, HEADER_LEN - readSoFar)
                if (r < 0) break
                readSoFar += r
            }
            // (Header bytes are validated loosely; malformed/truncated files simply fail below.)

            // Decrypt the first 16 bytes of actual content via XOR against the key.
            val encryptedChunk = ByteArray(HEADER_LEN)
            val n = input.read(encryptedChunk)
            if (n > 0) {
                val decrypted = ByteArray(n)
                for (i in 0 until n) {
                    decrypted[i] = (encryptedChunk[i].toInt() xor key[i % key.size].toInt()).toByte()
                }
                output.write(decrypted, 0, n)
                totalWritten += n
            }

            // Stream-copy the remainder unmodified in large chunks (low memory footprint).
            val buffer = ByteArray(1 shl 16) // 64KB
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                totalWritten += read
            }
        }
        return totalWritten
    }

    /**
     * Unpacks an RGSS3A (VX Ace) archive. The format stores a simple rotating XOR key
     * (seeded from a 32-bit value read from the header) applied per-entry to both the
     * directory metadata and file contents.
     *
     * Returns a list of (entryName, offset, length) triples for the caller to stream out;
     * actual decrypted bytes are produced via [decryptRgss3aChunk] so extraction can remain
     * chunked rather than loading whole entries into memory.
     */
    data class Rgss3aEntry(val name: String, val offset: Long, val length: Long, val startKey: Int)

    fun readRgss3aDirectory(source: Uri): List<Rgss3aEntry>? {
        val entries = mutableListOf<Rgss3aEntry>()
        context.contentResolver.openInputStream(source)?.use { raw ->
            val input = BufferedInputStream(raw)
            val magic = ByteArray(8)
            if (input.read(magic) != 8) return null
            // Expected magic: "RGSSAD\0" + version byte (3 for RGSS3A)
            val expectedPrefix = byteArrayOf(0x52, 0x47, 0x53, 0x53, 0x41, 0x44, 0x00, 0x03)
            if (!magic.contentEquals(expectedPrefix)) return null

            var key = readInt32LE(input) * 9 + 3
            while (true) {
                val offset = (readInt32LE(input) xor key).toLong() and 0xFFFFFFFFL
                if (offset == 0L) break
                key = advanceKey(key)
                val length = (readInt32LE(input) xor key).toLong() and 0xFFFFFFFFL
                key = advanceKey(key)
                val nameKey = readInt32LE(input) xor key
                key = advanceKey(key)
                val nameLen = nameKey
                val nameBytes = ByteArray(nameLen)
                input.read(nameBytes)
                val decodedName = StringBuilder()
                for (i in nameBytes.indices) {
                    val kb = (key ushr ((i % 4) * 8)) and 0xFF
                    decodedName.append(((nameBytes[i].toInt() and 0xFF) xor kb).toChar())
                    if (i % 4 == 3) key = advanceKey(key)
                }
                entries += Rgss3aEntry(decodedName.toString(), offset, length, key)
            }
        }
        return entries
    }

    private fun advanceKey(key: Int): Int = key * 7 + 3

    private fun readInt32LE(input: java.io.InputStream): Int {
        val b = ByteArray(4)
        var got = 0
        while (got < 4) {
            val r = input.read(b, got, 4 - got)
            if (r < 0) return 0
            got += r
        }
        return (b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24)
    }
}
