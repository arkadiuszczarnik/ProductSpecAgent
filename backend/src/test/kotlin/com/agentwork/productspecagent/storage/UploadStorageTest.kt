package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class UploadStorageTest {

    @TempDir lateinit var tempDir: Path

    private fun storage() = UploadStorage(tempDir.toString())

    @Test
    fun `save writes file under uploads and returns sanitized filename`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1, 2, 3), "2026-04-27T10:00:00Z")

        assertEquals("spec.pdf", name)
        val file = tempDir.resolve("projects/p1/uploads/spec.pdf")
        assertTrue(Files.exists(file))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(file))
    }

    @Test
    fun `save persists docId-filename mapping in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertTrue(Files.exists(index))
        val raw = Files.readString(index)
        assertTrue(raw.contains("\"doc-1\""))
        assertTrue(raw.contains("\"spec.pdf\""))
    }

    @Test
    fun `save with duplicate title appends auto-rename suffix`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")
        val second = s.save("p1", "doc-2", "spec.pdf", "application/pdf", byteArrayOf(2), "2026-04-27T10:00:00Z")
        val third = s.save("p1", "doc-3", "spec.pdf", "application/pdf", byteArrayOf(3), "2026-04-27T10:00:00Z")

        assertEquals("spec (2).pdf", second)
        assertEquals("spec (3).pdf", third)
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec.pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (2).pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (3).pdf")))
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
    fun `delete removes file and index entry`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", "application/pdf", byteArrayOf(1), "2026-04-27T10:00:00Z")

        s.delete("p1", "doc-1")

        assertFalse(Files.exists(tempDir.resolve("projects/p1/uploads/spec.pdf")))
        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertFalse(Files.readString(index).contains("\"doc-1\""))
    }

    @Test
    fun `delete is idempotent for missing docId`() {
        val s = storage()
        // No save first — index does not exist
        s.delete("p1", "missing")  // must not throw
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
    fun `list returns empty list when no uploads directory exists`() {
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

        val raw = java.nio.file.Files.readString(tempDir.resolve("projects/p1/uploads/.index.json"))
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
        val dir = tempDir.resolve("projects/p1/uploads")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.write(dir.resolve("legacy.pdf"), byteArrayOf(1, 2, 3))
        // Old-format index: flat docId -> filename map
        java.nio.file.Files.writeString(dir.resolve(".index.json"), """{"old-doc-1":"legacy.pdf"}""")

        val docs = s.listAsDocuments("p1")

        assertEquals(1, docs.size)
        val d = docs[0]
        assertEquals("old-doc-1", d.id)
        assertEquals("legacy.pdf", d.title)
        assertEquals("application/pdf", d.mimeType)  // inferred from extension
        // After migration, index should be in new format
        val rawAfter = java.nio.file.Files.readString(dir.resolve(".index.json"))
        assertTrue(rawAfter.contains("\"documents\""))
        assertTrue(rawAfter.contains("\"old-doc-1\""))
    }

    @Test
    fun `migration infers mimeType for common extensions`() {
        val s = storage()
        val dir = tempDir.resolve("projects/p1/uploads")
        java.nio.file.Files.createDirectories(dir)
        java.nio.file.Files.write(dir.resolve("a.md"), byteArrayOf(1))
        java.nio.file.Files.write(dir.resolve("b.txt"), byteArrayOf(1))
        java.nio.file.Files.write(dir.resolve("c.unknown"), byteArrayOf(1))
        java.nio.file.Files.writeString(dir.resolve(".index.json"),
            """{"d1":"a.md","d2":"b.txt","d3":"c.unknown"}""")

        val docs = s.listAsDocuments("p1").associateBy { it.id }

        assertEquals("text/markdown", docs["d1"]!!.mimeType)
        assertEquals("text/plain", docs["d2"]!!.mimeType)
        assertEquals("application/octet-stream", docs["d3"]!!.mimeType)
    }
}
