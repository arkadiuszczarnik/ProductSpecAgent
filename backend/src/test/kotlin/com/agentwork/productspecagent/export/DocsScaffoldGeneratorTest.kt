package com.agentwork.productspecagent.export

import org.junit.jupiter.api.Test
import kotlin.test.*

class DocsScaffoldGeneratorTest {

    private val generator = DocsScaffoldGenerator()

    private fun sampleContext() = ScaffoldContext(
        projectName = "Test App",
        features = listOf(
            FeatureContext(
                number = 1, title = "Auth", slug = "auth", filename = "01-auth.md",
                featureId = "feature-auth-1",
                description = "Authentication system", estimate = "L", dependencies = "—",
                stories = listOf(StoryContext(1, "Login Page", "Build login form")),
                acceptanceCriteria = listOf(TaskContext("Form works", "Submits credentials")),
                tasks = listOf(TaskContext("Login Page", "Build login form"), TaskContext("Form works", "Submits credentials"))
            )
        ),
        techStack = "Kotlin + Spring Boot",
    )

    @Test
    fun `generates feature overview`() {
        val result = generator.generate(sampleContext())
        val overview = result["docs/features/00-feature-set-overview.md"]
        assertNotNull(overview)
        assertContains(overview, "Test App")
        assertContains(overview, "Auth")
        assertContains(overview, "01-auth.md")
    }

    @Test
    fun `generates feature document per epic`() {
        val result = generator.generate(sampleContext())
        val feature = result["docs/features/01-auth.md"]
        assertNotNull(feature)
        assertContains(feature, "---\nfeature_id: feature-auth-1\n---")
        assertContains(feature, "Feature 1: Auth")
        assertContains(feature, "Login Page")
    }

    @Test
    fun `does not generate deprecated architecture backend or frontend docs`() {
        val result = generator.generate(sampleContext())
        assertFalse("docs/architecture/overview.md" in result)
        assertFalse("docs/backend/api.md" in result)
        assertFalse("docs/frontend/design.md" in result)
    }

    @Test
    fun `generates correct number of files`() {
        val result = generator.generate(sampleContext())
        // overview + 1 feature
        assertEquals(2, result.size)
    }
}
