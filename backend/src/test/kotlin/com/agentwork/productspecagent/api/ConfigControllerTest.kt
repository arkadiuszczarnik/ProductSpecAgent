package com.agentwork.productspecagent.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `GET features returns graphmeshEnabled true (test profile)`() {
        mockMvc.perform(get("/api/v1/config/features"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.graphmeshEnabled").value(true))
    }
}
