package com.agentwork.productspecagent.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals

class PromptRegistryTest {

    @Test
    fun `registry contains exactly the six expected prompts`() {
        val ids = PromptRegistry().definitions.map { it.id }.toSet()
        assertEquals(
            setOf("idea-base", "idea-marker-reminder", "idea-step-IDEA",
                  "decision-system", "plan-system", "feature-proposal-system"),
            ids,
        )
    }

    @Test
    fun `every definition has a loadable classpath resource`() {
        for (def in PromptRegistry().definitions) {
            val stream = this::class.java.getResourceAsStream(def.resourcePath)
            assertNotNull(stream, "Resource missing: ${def.resourcePath}")
            stream?.close()
        }
    }

    @Test
    fun `every default resource passes its own validators`() {
        for (def in PromptRegistry().definitions) {
            val content = this::class.java.getResourceAsStream(def.resourcePath)!!
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val errors = def.validators.flatMap { it.validate(content) }
            assertEquals(emptyList<String>(), errors,
                "Default for ${def.id} fails its own validators: $errors")
        }
    }

    @Test
    fun `byId throws PromptNotFoundException for unknown ids`() {
        org.junit.jupiter.api.assertThrows<PromptNotFoundException> {
            PromptRegistry().byId("nonexistent")
        }
    }
}
