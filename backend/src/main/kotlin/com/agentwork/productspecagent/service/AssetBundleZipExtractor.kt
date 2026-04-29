package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.ExtractedBundle
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

@Service
class AssetBundleZipExtractor {

    private val maxFileCount = 100
    private val maxFileSizeBytes = 2L * 1024 * 1024     // 2 MB per file
    private val maxTotalSizeBytes = 10L * 1024 * 1024   // 10 MB total

    private val json = Json { ignoreUnknownKeys = true }
    private val allowedSteps = setOf(FlowStepType.BACKEND, FlowStepType.FRONTEND, FlowStepType.ARCHITECTURE)
    private val allowedTopLevelFolders = setOf("skills", "commands", "agents")

    fun extract(bytes: ByteArray): ExtractedBundle {
        val rawFiles = readZip(bytes)
        val manifestBytes = rawFiles.remove("manifest.json")
            ?: throw MissingManifestException("manifest.json must be at the ZIP root")
        val manifest = parseManifest(manifestBytes)
        validateManifest(manifest)
        validateEntries(rawFiles.keys)
        return ExtractedBundle(manifest, rawFiles.toMap())
    }

    private fun readZip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var totalBytes = 0L

        val zis = try {
            ZipInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
        }
        zis.use { stream ->
            try {
                var entry = stream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !isFiltered(entry.name)) {
                        validatePath(entry.name)
                        val entryBytes = readEntryWithLimit(stream, entry.name)
                        totalBytes += entryBytes.size
                        if (totalBytes > maxTotalSizeBytes) {
                            throw BundleTooLargeException("Total bundle size exceeds 10 MB")
                        }
                        files[entry.name] = entryBytes
                        val nonManifestCount = files.keys.count { it != "manifest.json" }
                        if (nonManifestCount > maxFileCount) {
                            throw BundleTooLargeException("Too many files: > $maxFileCount")
                        }
                    }
                    stream.closeEntry()
                    entry = stream.nextEntry
                }
            } catch (e: ZipException) {
                throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
            }
        }
        if (files.isEmpty() && rawZipEmpty(bytes)) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: no entries found")
        }
        return files
    }

    private fun readEntryWithLimit(stream: ZipInputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxFileSizeBytes) {
                throw BundleTooLargeException("File too large: $name (limit ${maxFileSizeBytes / (1024 * 1024)} MB)")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun rawZipEmpty(bytes: ByteArray): Boolean {
        return bytes.size < 4 || !(bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())
    }

    private fun isFiltered(name: String): Boolean {
        if (name.endsWith(".DS_Store")) return true
        if (name.startsWith("__MACOSX/")) return true
        if (name.endsWith("Thumbs.db")) return true
        if (name.endsWith("desktop.ini")) return true
        val lastSegment = name.substringAfterLast('/')
        if (lastSegment.startsWith("._")) return true
        return false
    }

    private fun validatePath(name: String) {
        if (name.contains("../") || name.startsWith("/") || name.startsWith("\\")) {
            throw IllegalBundleEntryException(name, "path traversal blocked")
        }
        val normalized = java.nio.file.Paths.get(name).normalize().toString()
        if (normalized.replace('\\', '/') != name) {
            throw IllegalBundleEntryException(name, "non-canonical path")
        }
    }

    private fun parseManifest(bytes: ByteArray): AssetBundleManifest = try {
        json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        throw InvalidManifestException("manifest.json: ${e.message}")
    }

    private fun validateManifest(m: AssetBundleManifest) {
        if (m.step !in allowedSteps) throw UnsupportedStepException(m.step)
        val expectedId = assetBundleId(m.step, m.field, m.value)
        if (m.id != expectedId) throw ManifestIdMismatchException(expected = expectedId, actual = m.id)
        if (m.title.isBlank()) throw InvalidManifestException("Required field empty: title")
        if (m.description.isBlank()) throw InvalidManifestException("Required field empty: description")
        if (m.version.isBlank()) throw InvalidManifestException("Required field empty: version")
    }

    private fun validateEntries(paths: Set<String>) {
        for (path in paths) {
            val firstSegment = path.substringBefore('/')
            if (firstSegment == path) {
                throw IllegalBundleEntryException(path, "Top-level files not allowed (only manifest.json + skills/ commands/ agents/)")
            }
            if (firstSegment !in allowedTopLevelFolders) {
                throw IllegalBundleEntryException(path, "Top-level must be skills/, commands/, or agents/")
            }
        }
    }
}
