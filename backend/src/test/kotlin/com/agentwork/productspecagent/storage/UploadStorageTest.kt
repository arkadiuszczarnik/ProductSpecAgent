package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UploadStorageTest : S3TestSupport() {

    private fun storage() = UploadStorage(objectStore())

    @Test
    fun `save writes file under uploads and returns sanitized filename`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")

        assertEquals("spec.pdf", name)
        val bytes = objectStore().get("projects/p1/docs/uploads/spec.pdf")
        assertNotNull(bytes)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
    }

    @Test
    fun `save persists docId-filename mapping in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val raw = objectStore().get("projects/p1/docs/uploads/.index.json")
        assertNotNull(raw)
        val str = String(raw!!)
        assertTrue(str.contains("\"doc-1\""))
        assertTrue(str.contains("\"spec.pdf\""))
    }

    @Test
    fun `save with duplicate title appends auto-rename suffix`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")
        val second = s.save("p1", "doc-2", "spec.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T10:00:00Z")
        val third = s.save("p1", "doc-3", "spec.pdf", "application/pdf", byteArrayOf(3), "2026-04-27T10:00:00Z")

        assertEquals("spec (2).pdf", second)
        assertEquals("spec (3).pdf", third)
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec.pdf"))
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec (2).pdf"))
        assertTrue(objectStore().exists("projects/p1/docs/uploads/spec (3).pdf"))
    }

    @Test
    fun `save sanitizes path-traversal characters`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "../../etc/passwd", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
    }

    @Test
    fun `save with blank title falls back to document`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertEquals("document", name)
    }

    @Test
    fun `delete removes object and index entry`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        s.delete("p1", "doc-1")

        assertFalse(objectStore().exists("projects/p1/docs/uploads/spec.pdf"))
        val raw = objectStore().get("projects/p1/docs/uploads/.index.json")
        assertNotNull(raw)
        assertFalse(String(raw!!).contains("\"doc-1\""))
    }

    @Test
    fun `delete is idempotent for missing docId`() {
        val s = storage()
        s.delete("p1", "missing")
    }

    @Test
    fun `list returns filenames excluding index`() {
        val s = storage()
        s.save("p1", "doc-1", "a.md", "text/markdown", byteArrayOf(1), "2026-04-27T10:00:00Z")
        s.save("p1", "doc-2", "b.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T10:00:00Z")

        val files = s.list("p1")

        assertEquals(setOf("a.md", "b.pdf"), files.toSet())
    }

    @Test
    fun `list returns empty list when no uploads exist`() {
        val s = storage()
        assertEquals(emptyList<String>(), s.list("never-touched"))
    }

    @Test
    fun `read returns saved file bytes`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(7, 8, 9), "2026-04-27T10:00:00Z")

        val bytes = s.read("p1", "spec.pdf")

        assertArrayEquals(byteArrayOf(7, 8, 9), bytes)
    }

    @Test
    fun `save persists full metadata in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val raw = String(objectStore().get("projects/p1/docs/uploads/.index.json")!!)
        assertTrue(raw.contains("\"id\""))
        assertTrue(raw.contains("\"doc-1\""))
        assertTrue(raw.contains("\"spec.pdf\""))
        assertTrue(raw.contains("\"application/pdf\""))
        assertTrue(raw.contains("\"2026-04-27T10:00:00Z\""))
    }

    @Test
    fun `listAsDocuments returns document metadata in LOCAL state`() {
        val s = storage()
        s.save("p1", "d1", "a.md", "text/markdown", byteArrayOf(1), "2026-04-27T10:00:00Z")
        s.save("p1", "d2", "b.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T11:00:00Z")

        val docs = s.listAsDocuments("p1")

        assertEquals(2, docs.size)
        assertEquals(setOf("d1", "d2"), docs.map { it.id }.toSet())
        assertTrue(docs.all { it.state == com.agentwork.productspecagent.domain.DocumentState.LOCAL })
        val d1 = docs.first { it.id == "d1" }
        assertEquals("a.md", d1.title)
        assertEquals("text/markdown", d1.mimeType)
        assertEquals("2026-04-27T10:00:00Z", d1.createdAt)
    }

    @Test
    fun `getDocument returns the matching document`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val d = s.getDocument("p1", "doc-1")

        assertNotNull(d)
        assertEquals("spec.pdf", d!!.title)
    }

    @Test
    fun `getDocument returns null for missing docId`() {
        val s = storage()
        s.save("p1", "d1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        assertNull(s.getDocument("p1", "missing"))
    }

    @Test
    fun `migrates legacy index format on first read`() {
        val s = storage()
        objectStore().put("projects/p1/docs/uploads/legacy.pdf", byteArrayOf(1, 2, 3))
        objectStore().put(
            "projects/p1/docs/uploads/.index.json",
            """{"old-doc-1":"legacy.pdf"}""".toByteArray()
        )

        val docs = s.listAsDocuments("p1")

        assertEquals(1, docs.size)
        val d = docs[0]
        assertEquals("old-doc-1", d.id)
        assertEquals("legacy.pdf", d.title)
        assertEquals("application/pdf", d.mimeType)

        val rawAfter = String(objectStore().get("projects/p1/docs/uploads/.index.json")!!)
        assertTrue(rawAfter.contains("\"documents\""))
        assertTrue(rawAfter.contains("\"old-doc-1\""))
    }

    @Test
    fun `migration infers mimeType for common extensions`() {
        val s = storage()
        objectStore().put("projects/p1/docs/uploads/a.md", byteArrayOf(1))
        objectStore().put("projects/p1/docs/uploads/b.txt", byteArrayOf(1))
        objectStore().put("projects/p1/docs/uploads/c.unknown", byteArrayOf(1))
        objectStore().put(
            "projects/p1/docs/uploads/.index.json",
            """{"d1":"a.md","d2":"b.txt","d3":"c.unknown"}""".toByteArray()
        )

        val docs = s.listAsDocuments("p1").associateBy { it.id }

        assertEquals("text/markdown", docs["d1"]!!.mimeType)
        assertEquals("text/plain", docs["d2"]!!.mimeType)
        assertEquals("application/octet-stream", docs["d3"]!!.mimeType)
    }
}
