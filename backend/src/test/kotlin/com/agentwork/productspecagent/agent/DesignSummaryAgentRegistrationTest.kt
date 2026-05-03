package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.service.AgentModelService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DesignSummaryAgentRegistrationTest {

    @Autowired
    lateinit var agentModelService: AgentModelService

    @Test
    fun `getTier for design-summary does not throw and returns MEDIUM`() {
        assertThatCode {
            val tier = agentModelService.getTier(DesignSummaryAgent.AGENT_ID)
            assertThat(tier).isEqualTo(AgentModelTier.MEDIUM)
        }.doesNotThrowAnyException()
    }
}
