package com.agentwork.productspecagent.domain

import kotlinx.serialization.Serializable

@Serializable
data class AssetBundleManifest(
    val id: String,
    val step: FlowStepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AssetBundleFile(
    val relativePath: String,
    val size: Long,
    val contentType: String,
)

@Serializable
data class AssetBundle(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

/** Berechnet die deterministische Bundle-ID aus Triple (step, field, value). */
fun assetBundleId(step: FlowStepType, field: String, value: String): String =
    "${step.name.lowercase()}.$field.${assetBundleSlug(value)}"

/** Slugify: lowercase, [^a-z0-9]+ → "-", trim "-" an den Rändern. */
fun assetBundleSlug(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
