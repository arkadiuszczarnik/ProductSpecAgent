package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.AssetBundleUploadResult
import com.agentwork.productspecagent.storage.AssetBundleStorage
import org.springframework.stereotype.Service

@Service
class AssetBundleAdminService(
    private val storage: AssetBundleStorage,
    private val extractor: AssetBundleZipExtractor,
) {
    fun upload(zipBytes: ByteArray): AssetBundleUploadResult {
        val extracted = extractor.extract(zipBytes)
        // Clean-wipe before write: ensures stale files from previous version are gone
        storage.delete(extracted.manifest.step, extracted.manifest.field, extracted.manifest.value)
        storage.writeBundle(extracted.manifest, extracted.files)
        return AssetBundleUploadResult(extracted.manifest, extracted.files.size)
    }
}
