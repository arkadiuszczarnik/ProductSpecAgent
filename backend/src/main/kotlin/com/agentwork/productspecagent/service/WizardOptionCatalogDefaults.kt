package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FlowStepType
import com.agentwork.productspecagent.domain.FeatureScope
import com.agentwork.productspecagent.domain.WizardOption
import com.agentwork.productspecagent.domain.WizardOptionCatalog
import com.agentwork.productspecagent.domain.WizardOptionCategory
import com.agentwork.productspecagent.domain.WizardOptionField

object WizardOptionCatalogDefaults {

    private const val DEFAULT_UPDATED_AT = "2026-05-09T00:00:00Z"

    private val baseSteps = listOf(
        FlowStepType.IDEA,
        FlowStepType.PROBLEM,
        FlowStepType.FEATURES,
        FlowStepType.MVP,
    )

    private val saasMobileApiArchitecture = listOf(
        field(FlowStepType.ARCHITECTURE, "architecture", "Monolith", "Microservices", "Serverless"),
        field(FlowStepType.ARCHITECTURE, "database", "PostgreSQL", "MongoDB", "SQLite", "Redis"),
        field(FlowStepType.ARCHITECTURE, "deployment", "Docker", "Vercel+Cloud", "Kubernetes"),
    )

    private val saasApiBackend = listOf(
        field(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "Rust", "DotNet"),
        field(FlowStepType.BACKEND, "apiStyle", "REST", "GraphQL", "gRPC"),
        field(FlowStepType.BACKEND, "auth", "JWT", "Session", "OAuth", "API Key"),
    )

    fun create(): WizardOptionCatalog =
        WizardOptionCatalog(
            version = 1,
            updatedAt = DEFAULT_UPDATED_AT,
            categories = listOf(
                category(
                    id = "SaaS",
                    visibleSteps = baseSteps + listOf(FlowStepType.DESIGN, FlowStepType.ARCHITECTURE, FlowStepType.BACKEND, FlowStepType.FRONTEND),
                    allowedScopes = listOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
                    fields = saasMobileApiArchitecture + saasApiBackend + listOf(
                        field(FlowStepType.FRONTEND, "framework", "Next.js+React", "Vue+Nuxt", "Svelte", "Angular", "Stitch"),
                        field(FlowStepType.FRONTEND, "uiLibrary", "shadcn/ui", "Material UI", "Ant Design", "Custom"),
                        field(FlowStepType.FRONTEND, "styling", "Tailwind CSS", "CSS Modules", "Styled Components"),
                        field(FlowStepType.FRONTEND, "theme", "Dark only", "Light only", "Both"),
                    ),
                ),
                category(
                    id = "Mobile App",
                    visibleSteps = baseSteps + listOf(FlowStepType.DESIGN, FlowStepType.ARCHITECTURE, FlowStepType.BACKEND, FlowStepType.FRONTEND),
                    allowedScopes = listOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
                    fields = listOf(
                        field(FlowStepType.ARCHITECTURE, "architecture", "Monolith", "Microservices", "Serverless"),
                        field(FlowStepType.ARCHITECTURE, "database", "PostgreSQL", "MongoDB", "SQLite", "Firebase"),
                        field(FlowStepType.ARCHITECTURE, "deployment", "App Store", "Play Store", "TestFlight"),
                        field(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "Node+Express", "Python+FastAPI", "Go", "DotNet"),
                        field(FlowStepType.BACKEND, "apiStyle", "REST", "GraphQL"),
                        field(FlowStepType.BACKEND, "auth", "JWT", "OAuth", "API Key"),
                        field(FlowStepType.FRONTEND, "framework", "React Native", "Flutter", "SwiftUI", "Kotlin Multiplatform"),
                        field(FlowStepType.FRONTEND, "uiLibrary", "Native Components", "React Native Paper", "Custom"),
                        field(FlowStepType.FRONTEND, "styling", "StyleSheet", "NativeWind", "Styled Components"),
                        field(FlowStepType.FRONTEND, "theme", "System Default", "Dark only", "Light only", "Both"),
                    ),
                ),
                category(
                    id = "CLI Tool",
                    visibleSteps = baseSteps + FlowStepType.ARCHITECTURE,
                    allowedScopes = listOf(FeatureScope.BACKEND),
                    fields = listOf(
                        field(FlowStepType.ARCHITECTURE, "architecture", "Single Binary", "Multi-Command"),
                        field(FlowStepType.ARCHITECTURE, "database", "Filesystem", "SQLite"),
                        field(FlowStepType.ARCHITECTURE, "deployment", "npm/pip/brew", "Binary Release"),
                    ),
                ),
                category(
                    id = "Library",
                    visibleSteps = baseSteps,
                    allowedScopes = emptyList(),
                    fields = emptyList(),
                ),
                category(
                    id = "Desktop App",
                    visibleSteps = baseSteps + listOf(FlowStepType.DESIGN, FlowStepType.ARCHITECTURE, FlowStepType.BACKEND, FlowStepType.FRONTEND),
                    allowedScopes = listOf(FeatureScope.FRONTEND, FeatureScope.BACKEND),
                    fields = listOf(
                        field(FlowStepType.ARCHITECTURE, "architecture", "Monolith", "Plugin-basiert"),
                        field(FlowStepType.ARCHITECTURE, "database", "SQLite", "PostgreSQL", "Filesystem"),
                        field(FlowStepType.ARCHITECTURE, "deployment", "Installer", "App Store", "Portable"),
                        field(FlowStepType.BACKEND, "framework", "Kotlin+Spring", "Node+Express", "Python+FastAPI", "DotNet"),
                        field(FlowStepType.BACKEND, "apiStyle", "REST", "IPC"),
                        field(FlowStepType.BACKEND, "auth", "OAuth", "Local Auth"),
                        field(FlowStepType.FRONTEND, "framework", "Electron", "Tauri", "SwiftUI", "WPF"),
                        field(FlowStepType.FRONTEND, "uiLibrary", "Native Components", "shadcn/ui", "Custom"),
                        field(FlowStepType.FRONTEND, "styling", "Tailwind CSS", "Native Styling", "CSS Modules"),
                        field(FlowStepType.FRONTEND, "theme", "System Default", "Dark only", "Light only", "Both"),
                    ),
                ),
                category(
                    id = "API",
                    visibleSteps = baseSteps + listOf(FlowStepType.ARCHITECTURE, FlowStepType.BACKEND),
                    allowedScopes = listOf(FeatureScope.BACKEND),
                    fields = listOf(
                        field(FlowStepType.ARCHITECTURE, "architecture", "Monolith", "Microservices", "Serverless"),
                        field(FlowStepType.ARCHITECTURE, "database", "PostgreSQL", "MongoDB", "Redis"),
                        field(FlowStepType.ARCHITECTURE, "deployment", "Docker", "Vercel+Cloud", "Kubernetes"),
                    ) + saasApiBackend.filterNot { it.key == "auth" } + listOf(
                        field(FlowStepType.BACKEND, "auth", "JWT", "OAuth", "API Key"),
                    ),
                ),
            ),
        )

    private fun category(
        id: String,
        visibleSteps: List<FlowStepType>,
        allowedScopes: List<FeatureScope>,
        fields: List<WizardOptionField>,
    ): WizardOptionCategory =
        WizardOptionCategory(
            id = id,
            label = id,
            visibleSteps = visibleSteps,
            allowedScopes = allowedScopes,
            fields = fields,
        )

    private fun field(step: FlowStepType, key: String, vararg options: String): WizardOptionField =
        WizardOptionField(
            step = step,
            key = key,
            label = key,
            options = options.map { WizardOption(id = slug(it), label = it) },
        )

    private fun slug(label: String): String =
        label.trim()
            .lowercase()
            .replace("+", "-")
            .replace("/", "-")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
