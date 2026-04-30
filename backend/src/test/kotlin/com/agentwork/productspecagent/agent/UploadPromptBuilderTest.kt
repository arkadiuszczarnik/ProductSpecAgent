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

    @Test
    fun `renders markdown and plain-text uploads in createdAt order with markers`() {
        val storage = UploadStorage(InMemoryObjectStore())
        storage.save("p1", "d1", "second.md", "text/markdown", "## Second".toByteArray(),
            createdAt = "2026-01-02T00:00:00Z")
        storage.save("p1", "d2", "first.txt", "text/plain", "Plain content".toByteArray(),
            createdAt = "2026-01-01T00:00:00Z")

        val rendered = newBuilder(storage).renderUploadsSection("p1")

        val firstIdx = rendered.indexOf("--- BEGIN UPLOADED DOCUMENT: first.txt (text/plain) ---")
        val secondIdx = rendered.indexOf("--- BEGIN UPLOADED DOCUMENT: second.md (text/markdown) ---")
        assertThat(firstIdx).isGreaterThanOrEqualTo(0)
        assertThat(secondIdx).isGreaterThan(firstIdx)
        assertThat(rendered).contains("Plain content")
        assertThat(rendered).contains("## Second")
        assertThat(rendered).contains("--- END UPLOADED DOCUMENT ---")
    }

    @Test
    fun `skips PDF uploads but still renders MD when both present`() {
        val storage = UploadStorage(InMemoryObjectStore())
        storage.save("p1", "d1", "drawing.pdf", "application/pdf", byteArrayOf(0x25, 0x50, 0x44, 0x46))
        storage.save("p1", "d2", "notes.md", "text/markdown", "Important note".toByteArray())

        val rendered = newBuilder(storage).renderUploadsSection("p1")

        assertThat(rendered).contains("notes.md (text/markdown)")
        assertThat(rendered).doesNotContain("drawing.pdf")
    }
}
