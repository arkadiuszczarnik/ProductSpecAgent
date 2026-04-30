package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.config.FeatureProposalUploadsProperties
import com.agentwork.productspecagent.storage.InMemoryObjectStore
import com.agentwork.productspecagent.storage.UploadStorage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UploadPromptBuilderTest {

    private fun newBuilder(
        storage: UploadStorage = UploadStorage(InMemoryObjectStore()),
        maxBytesPerFile: Long = 102_400,
        maxBytesTotal: Long = 512_000,
    ): UploadPromptBuilder = UploadPromptBuilder(
        uploadStorage = storage,
        props = FeatureProposalUploadsProperties(maxBytesPerFile, maxBytesTotal),
    )

    @Test
    fun `returns empty string when project has no uploads`() {
        val builder = newBuilder()

        val rendered = builder.renderUploadsSection("p-empty")

        assertThat(rendered).isEmpty()
    }

    @Test
    fun `returns empty string when only PDF uploads exist`() {
        val storage = UploadStorage(InMemoryObjectStore())
        storage.save("p1", "d1", "spec.pdf", "application/pdf", byteArrayOf(0x25, 0x50))

        val rendered = newBuilder(storage).renderUploadsSection("p1")

        assertThat(rendered).isEmpty()
    }

    @Test
    fun `returns empty string when storage throws`() {
        val storage = object : UploadStorage(InMemoryObjectStore()) {
            override fun listAsDocuments(projectId: String): List<com.agentwork.productspecagent.domain.Document> =
                throw RuntimeException("object store down")
        }

        val rendered = newBuilder(storage).renderUploadsSection("p1")

        assertThat(rendered).isEmpty()
    }
}
