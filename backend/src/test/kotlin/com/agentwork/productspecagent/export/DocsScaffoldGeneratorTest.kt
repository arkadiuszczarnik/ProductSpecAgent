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
                description = "Authentication system", estimate = "L", dependencies = "—",
                stories = listOf(StoryContext(1, "Login Page", "Build login form")),
                acceptanceCriteria = listOf(TaskContext("Form works", "Submits credentials")),
                tasks = listOf(TaskContext("Login Page", "Build login form"), TaskContext("Form works", "Submits credentials"))
            )
        ),
        decisions = listOf(DecisionContext("Use JWT", "JWT tokens", "Stateless auth")),
        scopeContent = "Core auth features only.",
        mvpContent = "Login + Register.",
        techStack = "Kotlin + Spring Boot",
        problemContent = null,
        targetAudienceContent = null,
        architectureContent = null,
        backendContent = null,
        frontendContent = null
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
        assertContains(feature, "Feature 1: Auth")
        assertContains(feature, "Login Page")
    }

    @Test
    fun `generates architecture overview with decisions`() {
        val result = generator.generate(sampleContext())
        val arch = result["docs/architecture/overview.md"]
        assertNotNull(arch)
        assertContains(arch, "Use JWT")
        assertContains(arch, "Core auth features only.")
    }

    @Test
    fun `generates backend api doc`() {
        val result = generator.generate(sampleContext())
        assertNotNull(result["docs/backend/api.md"])
    }

    @Test
    fun `generates frontend design doc`() {
        val result = generator.generate(sampleContext())
        assertNotNull(result["docs/frontend/design.md"])
    }

    @Test
    fun `generates correct number of files`() {
        val result = generator.generate(sampleContext())
        // overview + 1 feature + architecture + backend + frontend = 5
        assertEquals(5, result.size)
    }

    private fun fullSpecContext() = ScaffoldContext(
        projectName = "Test App",
        features = emptyList(),
        decisions = emptyList(),
        scopeContent = "Core auth features only.",
        mvpContent = "Login + Register.",
        techStack = "Kotlin + Spring Boot",
        problemContent = "Users cannot manage credentials securely.",
        targetAudienceContent = "Enterprise developers needing SSO.",
        architectureContent = "Microservice architecture with API gateway.",
        backendContent = "REST API with Spring Boot, JWT auth.",
        frontendContent = "React SPA with shadcn/ui components."
    )

    @Test
    fun `architecture overview includes problem and target audience`() {
        val result = generator.generate(fullSpecContext())
        val arch = result["docs/architecture/overview.md"]!!
        assertContains(arch, "Users cannot manage credentials securely.")
        assertContains(arch, "Enterprise developers needing SSO.")
        assertContains(arch, "Microservice architecture with API gateway.")
    }

    @Test
    fun `backend api doc includes backend spec content`() {
        val result = generator.generate(fullSpecContext())
        val api = result["docs/backend/api.md"]!!
        assertContains(api, "REST API with Spring Boot, JWT auth.")
    }

    @Test
    fun `frontend design doc includes frontend spec content`() {
        val result = generator.generate(fullSpecContext())
        val design = result["docs/frontend/design.md"]!!
        assertContains(design, "React SPA with shadcn/ui components.")
    }
}
