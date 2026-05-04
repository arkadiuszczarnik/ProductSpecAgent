package com.agentwork.productspecagent.api

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.net.URI
import com.agentwork.productspecagent.service.ProjectService

@SpringBootTest
class DesignBundleControllerTest {

    @Autowired private lateinit var ctx: WebApplicationContext
    @Autowired private lateinit var projectService: ProjectService
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(ctx).build()
    }

    private fun createProject(name: String): String = projectService.createProject(name).project.id

    private val schedulerZip: ByteArray =
        java.io.File("../examples/Scheduler.zip").readBytes()

    @Test
    fun `upload returns bundle with derived URLs`() {
        val file = MockMultipartFile(
            "file", "Scheduler.zip", "application/zip", schedulerZip,
        )
        mockMvc.perform(
            multipart("/api/v1/projects/proj-c/design/upload").file(file)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.entryHtml").value("Scheduler.html"))
            .andExpect(jsonPath("$.pages.length()").value(5))
            .andExpect(jsonPath("$.entryUrl")
                .value("/api/v1/projects/proj-c/design/files/Scheduler.html"))
            .andExpect(jsonPath("$.bundleUrl")
                .value("/api/v1/projects/proj-c/design/files/"))
    }

    @Test
    fun `get returns 404 when no bundle`() {
        mockMvc.perform(get("/api/v1/projects/no-bundle/design"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `delete is idempotent`() {
        mockMvc.perform(delete("/api/v1/projects/missing/design"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `files endpoint serves with security headers and correct content-type`() {
        val file = MockMultipartFile("file", "Scheduler.zip", "application/zip", schedulerZip)
        mockMvc.perform(multipart("/api/v1/projects/proj-h/design/upload").file(file))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/v1/projects/proj-h/design/files/Scheduler.html"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("Content-Security-Policy"))
    }

    @Test
    fun `files endpoint rejects path traversal with 400`() {
        val file = MockMultipartFile("file", "Scheduler.zip", "application/zip", schedulerZip)
        mockMvc.perform(multipart("/api/v1/projects/proj-t/design/upload").file(file))
            .andExpect(status().isOk)

        // Use un-encoded "../" so Spring routes the request to the controller,
        // which then rejects the path traversal with 400 (mirrors AssetBundleController pattern).
        mockMvc.perform(get("/api/v1/projects/proj-t/design/files/../etc/passwd"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `files endpoint rejects URL-encoded path traversal with 400`() {
        val file = MockMultipartFile("file", "Scheduler.zip", "application/zip", schedulerZip)
        mockMvc.perform(multipart("/api/v1/projects/proj-tu/design/upload").file(file))
            .andExpect(status().isOk)

        // URL-encoded ..%2F variant — Spring routes the request and the controller's
        // post-decode traversal check should reject it.
        mockMvc.perform(
            MockMvcRequestBuilders.request(
                HttpMethod.GET,
                URI("/api/v1/projects/proj-tu/design/files/..%2F..%2Fetc%2Fpasswd"),
            )
        ).andExpect(status().isBadRequest)

        // Double-encoded dots variant
        mockMvc.perform(
            MockMvcRequestBuilders.request(
                HttpMethod.GET,
                URI("/api/v1/projects/proj-tu/design/files/%2e%2e%2fetc%2fpasswd"),
            )
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `upload returns 413 when zip exceeds limit`() {
        // Build a 6 MB zip using STORED (no compression) so the zip itself is > 5 MB.
        // Deflate compresses zeroes to almost nothing, so we need STORED + random-ish data.
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            val entry = java.util.zip.ZipEntry("big.bin")
            entry.method = java.util.zip.ZipEntry.STORED
            val data = ByteArray(6 * 1024 * 1024) { it.toByte() }
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = java.util.zip.CRC32().also { it.update(data) }.value
            zos.putNextEntry(entry)
            zos.write(data)
            zos.closeEntry()
        }
        val big = MockMultipartFile("file", "big.zip", "application/zip", baos.toByteArray())
        mockMvc.perform(multipart("/api/v1/projects/proj-big/design/upload").file(big))
            .andExpect(status().isPayloadTooLarge)
    }

    @Test
    fun `complete with bundle runs agent and advances flow`() {
        val projectId = createProject("Complete With Bundle Test")
        val file = MockMultipartFile("file", "Scheduler.zip", "application/zip", schedulerZip)
        mockMvc.perform(multipart("/api/v1/projects/$projectId/design/upload").file(file))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"locale":"de"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.nextStep").value("ARCHITECTURE"))
    }

    @Test
    fun `complete without bundle skips agent and advances flow`() {
        val projectId = createProject("Complete Without Bundle Test")
        mockMvc.perform(
            post("/api/v1/projects/$projectId/design/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"locale":"de"}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("übersprungen")))
            .andExpect(jsonPath("$.nextStep").value("ARCHITECTURE"))
    }
}
