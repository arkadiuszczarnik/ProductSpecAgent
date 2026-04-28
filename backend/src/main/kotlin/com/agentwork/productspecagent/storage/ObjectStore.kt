package com.agentwork.productspecagent.storage

import java.time.Instant

interface ObjectStore {

    /** Schreibt Bytes unter [key]. Überschreibt vorhandene Keys. */
    fun put(key: String, bytes: ByteArray, contentType: String? = null)

    /** Liest Bytes; null wenn der Key nicht existiert. */
    fun get(key: String): ByteArray?

    /** Existiert der Key? */
    fun exists(key: String): Boolean

    /** Löscht einzelnen Key (idempotent — wirft nicht bei NoSuchKey). */
    fun delete(key: String)

    /** Löscht alle Keys mit [prefix]. Batched bis zu 1000 Keys pro Request. */
    fun deletePrefix(prefix: String)

    /** Listet alle Keys mit [prefix]. Voll paginiert. */
    fun listKeys(prefix: String): List<String>

    /** Listet Keys + lastModified. Voll paginiert. */
    fun listEntries(prefix: String): List<ObjectEntry>

    /**
     * Listet die ersten Pfad-Segmente unter [prefix], getrennt durch [delimiter].
     * Beispiel: prefix="projects/", delimiter="/" → ["projects/abc/", "projects/def/"]
     * Trailing-Delimiter wird gestrippt → ["abc", "def"].
     */
    fun listCommonPrefixes(prefix: String, delimiter: String): List<String>

    data class ObjectEntry(val key: String, val lastModified: Instant)
}
