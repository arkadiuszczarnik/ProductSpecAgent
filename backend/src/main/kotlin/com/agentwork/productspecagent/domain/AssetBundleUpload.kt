package com.agentwork.productspecagent.domain

data class ExtractedBundle(
    val manifest: AssetBundleManifest,
    val files: Map<String, ByteArray>,
)

data class AssetBundleUploadResult(
    val manifest: AssetBundleManifest,
    val fileCount: Int,
)
