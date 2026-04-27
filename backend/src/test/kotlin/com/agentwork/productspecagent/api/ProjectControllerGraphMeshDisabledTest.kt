package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["graphmesh.enabled=false"])
class ProjectControllerGraphMeshDisabledTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"GM Off Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `PATCH graphmesh-enabled returns 409 when backend disabled`() {
        val pid = createProject()
        mockMvc.perform(
            patch("/api/v1/projects/$pid/graphmesh-enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true}""")
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("GRAPHMESH_DISABLED_BACKEND"))
    }

    @Test
    fun `PATCH graphmesh-enabled with enabled=false succeeds even when backend disabled`() {
        val pid = createProject()
        mockMvc.perform(
            patch("/api/v1/projects/$pid/graphmesh-enabled")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":false}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.graphmeshEnabled").value(false))
    }
}
