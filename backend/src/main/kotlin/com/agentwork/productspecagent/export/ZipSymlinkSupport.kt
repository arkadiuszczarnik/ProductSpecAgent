package com.agentwork.productspecagent.export

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipSymlinkSupport {
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
    private const val UNIX_VERSION_MADE_BY = 0x031E
    private const val SYMLINK_MODE = 0xA1FF

    fun ZipOutputStream.addSymlinkEntry(name: String, target: String) {
        putNextEntry(ZipEntry(name))
        write(target.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    fun patchSymlinks(zipBytes: ByteArray, symlinkNames: Set<String>): ByteArray {
        if (symlinkNames.isEmpty()) return zipBytes
        val patched = zipBytes.copyOf()
        var offset = 0
        while (offset <= patched.size - 46) {
            if (readIntLE(patched, offset) != CENTRAL_DIRECTORY_SIGNATURE) {
                offset += 1
                continue
            }

            val nameLength = readShortLE(patched, offset + 28)
            val extraLength = readShortLE(patched, offset + 30)
            val commentLength = readShortLE(patched, offset + 32)
            val nameStart = offset + 46
            val nameEnd = nameStart + nameLength
            if (nameEnd > patched.size) break

            val name = patched.decodeToString(nameStart, nameEnd)
            if (name in symlinkNames) {
                writeShortLE(patched, offset + 4, UNIX_VERSION_MADE_BY)
                writeIntLE(patched, offset + 38, SYMLINK_MODE shl 16)
            }
            offset = nameEnd + extraLength + commentLength
        }
        return patched
    }

    fun centralDirectoryUnixMode(zipBytes: ByteArray, entryName: String): Int? {
        var offset = 0
        while (offset <= zipBytes.size - 46) {
            if (readIntLE(zipBytes, offset) != CENTRAL_DIRECTORY_SIGNATURE) {
                offset += 1
                continue
            }

            val nameLength = readShortLE(zipBytes, offset + 28)
            val extraLength = readShortLE(zipBytes, offset + 30)
            val commentLength = readShortLE(zipBytes, offset + 32)
            val nameStart = offset + 46
            val nameEnd = nameStart + nameLength
            if (nameEnd > zipBytes.size) return null

            val name = zipBytes.decodeToString(nameStart, nameEnd)
            if (name == entryName) {
                return readIntLE(zipBytes, offset + 38) ushr 16
            }
            offset = nameEnd + extraLength + commentLength
        }
        return null
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeShortLE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    private fun writeIntLE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
