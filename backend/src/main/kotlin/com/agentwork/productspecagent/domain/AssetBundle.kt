package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AssetBundleScope {
    MATCHED,
    GLOBAL,
}

@Serializable
data class AssetBundleManifest(
    val id: String,
    val scope: AssetBundleScope = AssetBundleScope.MATCHED,
    val step: FlowStepType? = null,
    val field: String? = null,
    val value: String? = null,
    val version: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
)

data class AssetBundleFile(
    val relativePath: String,
    val size: Long,
    val contentType: String,
)

data class AssetBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

/** Berechnet die deterministische Bundle-ID aus Triple (step, field, value). */
fun assetBundleId(step: FlowStepType, field: String, value: String): String =
    "${step.name.lowercase()}.$field.${assetBundleSlug(value)}"

fun assetBundleId(manifest: AssetBundleManifest): String =
    when (manifest.scope) {
        AssetBundleScope.GLOBAL -> manifest.id
        AssetBundleScope.MATCHED -> assetBundleId(
            requireNotNull(manifest.step) { "Matched asset bundle requires step" },
            requireNotNull(manifest.field) { "Matched asset bundle requires field" },
            requireNotNull(manifest.value) { "Matched asset bundle requires value" },
        )
    }

/** Slugify: lowercase, [^a-z0-9]+ → "-", trim "-" an den Rändern. */
fun assetBundleSlug(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
