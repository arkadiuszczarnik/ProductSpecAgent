package com.agentwork.productspecagent.export

import com.agentwork.productspecagent.domain.HandoffExportRequest
import com.agentwork.productspecagent.domain.HandoffPreview
import org.springframework.stereotype.Service

@Service
class HandoffService(
    private val packageBuilder: ExportPackageBuilder,
) {
    fun generatePreview(projectId: String, format: String, syncUrl: String): HandoffPreview =
        packageBuilder.previewHandoff(projectId, syncUrl, HandoffFormat.fromWire(format))

    @Suppress("UNUSED_PARAMETER")
    fun exportHandoff(projectId: String, request: HandoffExportRequest, syncUrl: String, flat: Boolean = true): ByteArray {
        val effectiveSyncUrl = request.syncUrl ?: syncUrl
        return packageBuilder.exportHandoff(projectId, effectiveSyncUrl, request.toHandoffPackageOptions()).bytes
    }
}
