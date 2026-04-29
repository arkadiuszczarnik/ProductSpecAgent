package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.assetBundleId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val json = Json { ignoreUnknownKeys = true }

fun sampleManifest(
    step: FlowStepType = FlowStepType.BACKEND,
    field: String = "framework",
    value: String = "Kotlin+Spring",
    id: String = assetBundleId(step, field, value),
    title: String = "Kotlin + Spring Boot Essentials",
    description: String = "Skills für Spring",
    version: String = "1.0.0",
) = AssetBundleManifest(
    id = id, step = step, field = field, value = value,
    version = version, title = title, description = description,
    createdAt = "2026-04-29T12:00:00Z",
    updatedAt = "2026-04-29T12:00:00Z",
)

/** Builds a ZIP byte-array from manifest (optional) + file map + raw extras. */
fun buildZip(
    manifest: AssetBundleManifest? = sampleManifest(),
    files: Map<String, ByteArray> = emptyMap(),
    rawExtras: Map<String, ByteArray> = emptyMap(),
): ByteArray {
    val baos = ByteArrayOutputStream()
    ZipOutputStream(baos).use { zip ->
        if (manifest != null) {
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
        }
        files.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
        rawExtras.forEach { (path, bytes) ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return baos.toByteArray()
}

fun bytesOfSize(size: Int): ByteArray = ByteArray(size) { 'a'.code.toByte() }
