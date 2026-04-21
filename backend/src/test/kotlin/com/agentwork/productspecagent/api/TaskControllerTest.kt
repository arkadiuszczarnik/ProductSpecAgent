package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.agent.PlanGeneratorAgent
import com.agentwork.productspecagent.agent.SpecContextBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testPlanAgent(ctx: SpecContextBuilder) = object : PlanGeneratorAgent(ctx) {
            override suspend fun runAgent(prompt: String) = """{"epics":[{"title":"Setup","description":"Project setup","estimate":"L","specSection":"IDEA","stories":[{"title":"Init","description":"Initialize","estimate":"M","tasks":[{"title":"Scaffold","description":"Create structure","estimate":"S"}]}]}]}"""
        }
    }

    @Autowired lateinit var mockMvc: MockMvc

    private fun createProject(): String {
        val result = mockMvc.perform(
            post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Task Test"}""")
        ).andExpect(status().isCreated()).andReturn()
        return """"id"\s*:\s*"([^"]+)"""".toRegex().find(result.response.contentAsString)!!.groupValues[1]
    }

    @Test
    fun `POST generate creates tasks`() {
        val pid = createProject()
        mockMvc.perform(post("/api/v1/projects/$pid/tasks/generate"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(3)) // 1 epic + 1 story + 1 task
    }

    @Test
    fun `GET list returns tasks`() {
        val pid = createProject()
        mockMvc.perform(post("/api/v1/projects/$pid/tasks/generate"))
            .andExpect(status().isCreated())

        mockMvc.perform(get("/api/v1/projects/$pid/tasks"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
    }

    @Test
    fun `PUT updates a task`() {
        val pid = createProject()
        val genResult = mockMvc.perform(post("/api/v1/projects/$pid/tasks/generate"))
            .andExpect(status().isCreated()).andReturn()

        val taskId = """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(genResult.response.contentAsString)!!.groupValues[1]

        mockMvc.perform(
            put("/api/v1/projects/$pid/tasks/$taskId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Updated Title","status":"IN_PROGRESS"}""")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
    }

    @Test
    fun `GET coverage returns coverage map`() {
        val pid = createProject()
        mockMvc.perform(post("/api/v1/projects/$pid/tasks/generate"))

        mockMvc.perform(get("/api/v1/projects/$pid/tasks/coverage"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.IDEA").value(true))
    }

    @Test
    fun `GET non-existent task returns 404`() {
        val pid = createProject()
        mockMvc.perform(get("/api/v1/projects/$pid/tasks/non-existent"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun `DELETE removes a task`() {
        val pid = createProject()
        val genResult = mockMvc.perform(post("/api/v1/projects/$pid/tasks/generate"))
            .andExpect(status().isCreated()).andReturn()
        val taskId = """"id"\s*:\s*"([^"]+)"""".toRegex()
            .find(genResult.response.contentAsString)!!.groupValues[1]

        mockMvc.perform(delete("/api/v1/projects/$pid/tasks/$taskId"))
            .andExpect(status().isNoContent())
    }
}
