package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.domain.AssetBundleManifest
import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.storage.ObjectStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class AssetBundleControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectStore: ObjectStore

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun cleanBundles() {
        objectStore.deletePrefix("asset-bundles/")
    }

    private fun putBundle(id: String, step: FlowStepType, field: String, value: String, files: Map<String, String> = emptyMap()) {
        val m = AssetBundleManifest(
            id = id, step = step, field = field, value = value,
            version = "1.0.0", title = "T", description = "D",
            createdAt = "2026-04-29T12:00:00Z", updatedAt = "2026-04-29T12:00:00Z",
        )
        objectStore.put("asset-bundles/$id/manifest.json", json.encodeToString(m).toByteArray())
        files.forEach { (rel, content) ->
            objectStore.put("asset-bundles/$id/$rel", content.toByteArray())
        }
    }

    @Test
    fun `GET asset-bundles returns empty array when none exist`() {
        mockMvc.perform(get("/api/v1/asset-bundles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET asset-bundles lists all bundles with file count`() {
        putBundle("backend.framework.kotlin-spring", FlowStepType.BACKEND, "framework", "Kotlin+Spring",
                  files = mapOf("skills/x/SKILL.md" to "a", "commands/y.md" to "b"))
        putBundle("frontend.framework.stitch", FlowStepType.FRONTEND, "framework", "Stitch",
                  files = mapOf("skills/z/SKILL.md" to "c"))

        mockMvc.perform(get("/api/v1/asset-bundles"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.id == 'backend.framework.kotlin-spring')].fileCount").value(2))
            .andExpect(jsonPath("$[?(@.id == 'frontend.framework.stitch')].fileCount").value(1))
    }

    @Test
    fun `GET asset-bundle detail returns manifest and files`() {
        putBundle("backend.framework.kotlin-spring", FlowStepType.BACKEND, "framework", "Kotlin+Spring",
                  files = mapOf("skills/x/SKILL.md" to "a"))

        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Kotlin+Spring"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.manifest.id").value("backend.framework.kotlin-spring"))
            .andExpect(jsonPath("$.files.length()").value(1))
            .andExpect(jsonPath("$.files[0].relativePath").value("skills/x/SKILL.md"))
            .andExpect(jsonPath("$.files[0].contentType").value("text/markdown"))
    }

    @Test
    fun `GET asset-bundle detail returns 404 for unknown triple`() {
        mockMvc.perform(get("/api/v1/asset-bundles/BACKEND/framework/Nonexistent"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("NOT_FOUND"))
    }

    @Test
    fun `GET asset-bundle detail returns 400 for invalid step enum`() {
        mockMvc.perform(get("/api/v1/asset-bundles/INVALID_STEP/framework/Kotlin+Spring"))
            .andExpect(status().isBadRequest)
    }
}
