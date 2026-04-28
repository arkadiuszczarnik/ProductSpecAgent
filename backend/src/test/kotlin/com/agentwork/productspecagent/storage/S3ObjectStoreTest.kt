package com.agentwork.productspecagent.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class S3ObjectStoreTest : S3TestSupport() {

    @Test
    fun `put then get round-trips bytes`() {
        val store = objectStore()
        store.put("a.txt", "hello".toByteArray())

        val bytes = store.get("a.txt")

        assertNotNull(bytes)
        assertEquals("hello", String(bytes!!))
    }

    @Test
    fun `get returns null for missing key`() {
        assertNull(objectStore().get("does-not-exist"))
    }

    @Test
    fun `exists returns false for missing key, true after put`() {
        val store = objectStore()

        assertFalse(store.exists("k"))
        store.put("k", byteArrayOf(1))
        assertTrue(store.exists("k"))
    }

    @Test
    fun `delete is idempotent for missing key`() {
        assertDoesNotThrow { objectStore().delete("ghost") }
    }

    @Test
    fun `delete removes key`() {
        val store = objectStore()
        store.put("k", byteArrayOf(1))

        store.delete("k")

        assertNull(store.get("k"))
    }

    @Test
    fun `deletePrefix removes only matching keys`() {
        val store = objectStore()
        store.put("projects/a/file1", byteArrayOf(1))
        store.put("projects/a/file2", byteArrayOf(2))
        store.put("projects/b/file1", byteArrayOf(3))

        store.deletePrefix("projects/a/")

        assertNull(store.get("projects/a/file1"))
        assertNull(store.get("projects/a/file2"))
        assertNotNull(store.get("projects/b/file1"))
    }

    @Test
    fun `listKeys returns all keys with prefix`() {
        val store = objectStore()
        store.put("p/a", byteArrayOf(1))
        store.put("p/b", byteArrayOf(2))
        store.put("other/c", byteArrayOf(3))

        val keys = store.listKeys("p/").toSet()

        assertEquals(setOf("p/a", "p/b"), keys)
    }

    @Test
    fun `listKeys returns empty for unknown prefix`() {
        assertEquals(emptyList<String>(), objectStore().listKeys("nope/"))
    }

    @Test
    fun `listEntries returns key plus lastModified`() {
        val store = objectStore()
        val before = Instant.now().minusSeconds(5)
        store.put("p/a", byteArrayOf(1))

        val entries = store.listEntries("p/")

        assertEquals(1, entries.size)
        assertEquals("p/a", entries[0].key)
        assertTrue(entries[0].lastModified.isAfter(before))
    }

    @Test
    fun `listCommonPrefixes returns directory-like segments`() {
        val store = objectStore()
        store.put("projects/a/file", byteArrayOf(1))
        store.put("projects/b/file", byteArrayOf(2))
        store.put("projects/c/sub/file", byteArrayOf(3))

        val prefixes = store.listCommonPrefixes("projects/", "/").toSet()

        assertEquals(setOf("a", "b", "c"), prefixes)
    }

    @Test
    fun `deletePrefix handles batches over 1000 keys`() {
        val store = objectStore()
        repeat(1100) { i -> store.put("bulk/$i", byteArrayOf(i.toByte())) }

        store.deletePrefix("bulk/")

        assertEquals(0, store.listKeys("bulk/").size)
    }
}
