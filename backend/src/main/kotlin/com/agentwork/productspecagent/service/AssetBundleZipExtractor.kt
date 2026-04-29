package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.ExtractedBundle
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

@Service
class AssetBundleZipExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    fun extract(bytes: ByteArray): ExtractedBundle {
        val rawFiles = readZip(bytes)
        val manifestBytes = rawFiles.remove("manifest.json")
            ?: throw MissingManifestException("manifest.json must be at the ZIP root")
        val manifest = parseManifest(manifestBytes)
        return ExtractedBundle(manifest, rawFiles.toMap())
    }

    private fun readZip(bytes: ByteArray): MutableMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val zis = try {
            ZipInputStream(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
        }
        zis.use { stream ->
            try {
                var entry = stream.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        files[entry.name] = stream.readAllBytes()
                    }
                    stream.closeEntry()
                    entry = stream.nextEntry
                }
            } catch (e: ZipException) {
                throw IllegalBundleEntryException("(zip)", "Invalid ZIP file: ${e.message}")
            }
        }
        return files
    }

    private fun parseManifest(bytes: ByteArray): AssetBundleManifest = try {
        json.decodeFromString<AssetBundleManifest>(bytes.toString(Charsets.UTF_8))
    } catch (e: Exception) {
        throw InvalidManifestException("manifest.json: ${e.message}")
    }
}
