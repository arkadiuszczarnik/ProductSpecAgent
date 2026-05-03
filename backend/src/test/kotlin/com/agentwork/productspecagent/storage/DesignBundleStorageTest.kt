package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.config.DesignBundleProperties
import com.agentwork.productspecagent.service.DesignBundleExtractor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesignBundleStorageTest {

    private fun newStorage(@Suppress("UNUSED_PARAMETER") tmp: Path? = null): DesignBundleStorage {
        val store = InMemoryObjectStore()
        val extractor = DesignBundleExtractor(DesignBundleProperties())
        return DesignBundleStorage(store, extractor)
    }

    private val schedulerZip: ByteArray =
        java.io.File("../examples/Scheduler.zip").readBytes()

    @Test
    fun `save returns bundle with project id and persists manifest`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        val bundle = storage.save("proj-x", "Scheduler.zip", schedulerZip)
        assertThat(bundle.projectId).isEqualTo("proj-x")
        assertThat(bundle.entryHtml).isEqualTo("Scheduler.html")
        assertThat(bundle.pages).hasSize(5)

        val reloaded = storage.get("proj-x")
        assertThat(reloaded).isNotNull
        assertThat(reloaded!!.pages.map { it.id })
            .containsExactlyInAnyOrder("login", "table", "timeline", "calendar", "pools")
    }

    @Test
    fun `get returns null when no bundle exists`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        assertThat(storage.get("nope")).isNull()
    }

    @Test
    fun `readFile returns extracted file bytes`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        storage.save("proj-y", "Scheduler.zip", schedulerZip)
        val html = storage.readFile("proj-y", "Scheduler.html")
        assertThat(String(html, Charsets.UTF_8)).contains("design-canvas.jsx")
    }

    @Test
    fun `readFile rejects path traversal`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        storage.save("proj-z", "Scheduler.zip", schedulerZip)
        assertThrows<IllegalArgumentException> {
            storage.readFile("proj-z", "../../../etc/passwd")
        }
    }

    @Test
    fun `delete removes bundle and files`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        storage.save("proj-d", "Scheduler.zip", schedulerZip)
        storage.delete("proj-d")
        assertThat(storage.get("proj-d")).isNull()
    }

    @Test
    fun `save replaces existing bundle atomically`(@TempDir tmp: Path) {
        val storage = newStorage(tmp)
        storage.save("proj-r", "Scheduler.zip", schedulerZip)
        val replaced = storage.save("proj-r", "Scheduler.zip", schedulerZip)
        // Files re-extracted, manifest re-written, listing only one entry per path
        assertThat(replaced.files.distinctBy { it.path }).hasSize(replaced.files.size)
    }
}
