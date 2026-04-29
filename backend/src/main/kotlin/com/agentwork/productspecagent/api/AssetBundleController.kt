package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AssetBundleFile
import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.service.AssetBundleNotFoundException
import com.agentwork.productspecagent.storage.AssetBundleStorage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AssetBundleListItem(
    val id: String,
    val step: FlowStepType,
    val field: String,
    val value: String,
    val version: String,
    val title: String,
    val description: String,
    val fileCount: Int,
)

data class AssetBundleDetail(
    val manifest: AssetBundleManifest,
    val files: List<AssetBundleFile>,
)

@RestController
@RequestMapping("/api/v1/asset-bundles")
class AssetBundleController(private val storage: AssetBundleStorage) {

    @GetMapping
    fun list(): List<AssetBundleListItem> =
        storage.listAll().map { manifest ->
            val bundle = storage.find(manifest.step, manifest.field, manifest.value)
            AssetBundleListItem(
                id = manifest.id,
                step = manifest.step,
                field = manifest.field,
                value = manifest.value,
                version = manifest.version,
                title = manifest.title,
                description = manifest.description,
                fileCount = bundle?.files?.size ?: 0,
            )
        }

    @GetMapping("/{step}/{field}/{value}")
    fun detail(
        @PathVariable step: FlowStepType,
        @PathVariable field: String,
        @PathVariable value: String,
    ): AssetBundleDetail {
        val bundle = storage.find(step, field, value)
            ?: throw AssetBundleNotFoundException("${step.name.lowercase()}.$field.$value")
        return AssetBundleDetail(manifest = bundle.manifest, files = bundle.files)
    }
}
