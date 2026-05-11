package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WizardBlockerApplyTest {

    @Test
    fun `parseResult keeps only allowed fields`() {
        val result = WizardBlockerApplyJson.parseResult(
            raw = """
                {
                  "message": "Applied.",
                  "fieldUpdates": {
                    "primaryAudience": "B2B SaaS teams",
                    "unknown": "ignored"
                  }
                }
            """.trimIndent(),
            allowedFields = setOf("primaryAudience"),
        )

        assertThat(result.message).isEqualTo("Applied.")
        assertThat(result.fieldUpdates).containsOnlyKeys("primaryAudience")
        assertThat(result.fieldUpdates["primaryAudience"]).isEqualTo(JsonPrimitive("B2B SaaS teams"))
        assertThat(result.appliedFields).containsExactly("primaryAudience")
    }

    @Test
    fun `parseResult returns safe empty result for invalid JSON`() {
        val result = WizardBlockerApplyJson.parseResult(
            raw = "not json",
            allowedFields = setOf("coreProblem"),
        )

        assertThat(result.message).isEqualTo("Die Antwort wurde beruecksichtigt.")
        assertThat(result.fieldUpdates).isEmpty()
        assertThat(result.appliedFields).isEmpty()
    }

    @Test
    fun `allowed fields use persisted MVP goal key`() {
        assertThat(WizardStepFieldSchema.allowedFields(FlowStepType.MVP))
            .contains("goal")
            .doesNotContain("mvpGoal")
    }
}
