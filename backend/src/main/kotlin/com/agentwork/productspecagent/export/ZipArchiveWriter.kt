package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.export.ZipSymlinkSupport.addSymlinkEntry
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveWriter(rootPrefix: String = "") {
    private val root = rootPrefix.trim('/')
    private val baos = ByteArrayOutputStream()
    private val zip = ZipOutputStream(baos)
    private val entries = mutableSetOf<String>()
    private val symlinkEntries = mutableSetOf<String>()
    private var finishedBytes: ByteArray? = null

    fun addText(path: String, content: String) {
        addBytes(path, content.toByteArray(Charsets.UTF_8))
    }

    fun addBytes(path: String, content: ByteArray) {
        zip.putNextEntry(ZipEntry(newEntryName(path)))
        zip.write(content)
        zip.closeEntry()
    }

    fun addDirectory(path: String) {
        zip.putNextEntry(ZipEntry(newDirectoryEntryName(path)))
        zip.closeEntry()
    }

    fun addSymlink(path: String, target: String) {
        val name = newEntryName(path)
        zip.addSymlinkEntry(name, target)
        symlinkEntries += name
    }

    fun hasEntry(path: String): Boolean = entryName(path) in entries

    fun finish(): ByteArray {
        finishedBytes?.let { return it }
        zip.close()
        val patched = ZipSymlinkSupport.patchSymlinks(baos.toByteArray(), symlinkEntries)
        finishedBytes = patched
        return patched
    }

    private fun entryName(path: String): String {
        val relative = path.trimStart('/')
        return if (root.isBlank()) relative else "$root/$relative"
    }

    private fun newEntryName(path: String): String {
        val name = entryName(path)
        check(entries.add(name)) { "Duplicate ZIP entry: $name" }
        return name
    }

    private fun newDirectoryEntryName(path: String): String =
        newEntryName(path.trimEnd('/') + "/")
}
