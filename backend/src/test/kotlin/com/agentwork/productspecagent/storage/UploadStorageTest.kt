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
        val name = s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1, 2, 3))

        assertEquals("spec.pdf", name)
        val file = tempDir.resolve("projects/p1/uploads/spec.pdf")
        assertTrue(Files.exists(file))
        assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(file))
    }

    @Test
    fun `save persists docId-filename mapping in index`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))

        val index = tempDir.resolve("projects/p1/uploads/.index.json")
        assertTrue(Files.exists(index))
        assertTrue(Files.readString(index).contains("\"doc-1\""))
        assertTrue(Files.readString(index).contains("\"spec.pdf\""))
    }

    @Test
    fun `save with duplicate title appends auto-rename suffix`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))
        val second = s.save("p1", "doc-2", "spec.pdf", byteArrayOf(2))
        val third = s.save("p1", "doc-3", "spec.pdf", byteArrayOf(3))

        assertEquals("spec (2).pdf", second)
        assertEquals("spec (3).pdf", third)
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec.pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (2).pdf")))
        assertTrue(Files.exists(tempDir.resolve("projects/p1/uploads/spec (3).pdf")))
    }

    @Test
    fun `save sanitizes path-traversal characters`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "../../etc/passwd", byteArrayOf(1))

        assertFalse(name.contains(".."))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
    }

    @Test
    fun `save with blank title falls back to document`() {
        val s = storage()
        val name = s.save("p1", "doc-1", "", byteArrayOf(1))

        assertEquals("document", name)
    }

    @Test
    fun `delete removes file and index entry`() {
        val s = storage()
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(1))

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
        s.save("p1", "doc-1", "a.md", byteArrayOf(1))
        s.save("p1", "doc-2", "b.pdf", byteArrayOf(2))

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
        s.save("p1", "doc-1", "spec.pdf", byteArrayOf(7, 8, 9))

        val bytes = s.read("p1", "spec.pdf")

        assertArrayEquals(byteArrayOf(7, 8, 9), bytes)
    }
}
