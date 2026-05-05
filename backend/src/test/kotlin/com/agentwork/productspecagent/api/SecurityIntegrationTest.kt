package com.agentwork.productspecagent.api

import com.agentwork.productspecagent.storage.S3TestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest : S3TestSupport() {

    @Autowired lateinit var mvc: MockMvc

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("auth.jwt.secret") { "0123456789abcdef0123456789abcdef0123456789abcdef" }
            registry.add("auth.cookie.secure") { "false" }
            registry.add("app.storage.bucket") { BUCKET }
            registry.add("app.storage.endpoint") { minio.s3URL }
            registry.add("app.storage.access-key") { minio.userName }
            registry.add("app.storage.secret-key") { minio.password }
            registry.add("app.storage.path-style-access") { "true" }
        }
    }

    private fun registerBody(email: String, pw: String) =
        """{"email":"$email","password":"$pw"}"""

    @Test
    fun `health endpoint accessible without auth`() {
        mvc.perform(get("/api/health")).andExpect(status().isOk)
    }

    @Test
    fun `projects endpoint without cookie returns 401`() {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `projects endpoint with valid session cookie does not return 401`() {
        val res = mvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(registerBody("user@example.com", "password123")))
            .andReturn().response
        val cookie = res.getCookie("session")!!

        // Don't expect a specific success code — auth gate just shouldn't reject with 401.
        val result = mvc.perform(get("/api/v1/projects").cookie(cookie)).andReturn()
        assert(result.response.status != 401) {
            "Expected request through auth gate, got 401"
        }
    }
}
