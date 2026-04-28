package com.agentwork.productspecagent.storage

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** Lightweight in-memory ObjectStore for unit tests that don't need real S3/MinIO. */
class InMemoryObjectStore : ObjectStore {

    private val store = ConcurrentHashMap<String, ByteArray>()

    override fun put(key: String, bytes: ByteArray, contentType: String?) {
        store[key] = bytes
    }

    override fun get(key: String): ByteArray? = store[key]

    override fun exists(key: String): Boolean = store.containsKey(key)

    override fun delete(key: String) {
        store.remove(key)
    }

    override fun deletePrefix(prefix: String) {
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    override fun listKeys(prefix: String): List<String> =
        store.keys.filter { it.startsWith(prefix) }.sorted()

    override fun listEntries(prefix: String): List<ObjectStore.ObjectEntry> =
        listKeys(prefix).map { ObjectStore.ObjectEntry(it, Instant.EPOCH) }

    override fun listCommonPrefixes(prefix: String, delimiter: String): List<String> {
        val result = mutableSetOf<String>()
        for (key in store.keys) {
            if (!key.startsWith(prefix)) continue
            val rest = key.removePrefix(prefix)
            val delimIdx = rest.indexOf(delimiter)
            if (delimIdx >= 0) {
                result.add(rest.substring(0, delimIdx))
            }
        }
        return result.sorted()
    }
}
