package com.agentwork.productspecagent.agent

import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

@Serializable
data class GeneratedDesignVariant(
    val title: String,
    val html: String,
    val rationale: String,
)

@Component
open class DesignVariantAgent(private val koogRunner: KoogAgentRunner? = null) {
    companion object {
        const val AGENT_ID = "design-variant"
    }

    open fun generate(projectId: String, screenId: String, prompt: String?): GeneratedDesignVariant =
        GeneratedDesignVariant(
            title = "Initial",
            html = """
                <!doctype html>
                <html>
                  <head><meta charset="utf-8"><style>body{font-family:system-ui;margin:32px}</style></head>
                  <body><main><h1>${screenId.replaceFirstChar { it.uppercase() }}</h1><p>${prompt ?: "Generated design variant"}</p></main></body>
                </html>
            """.trimIndent(),
            rationale = "Fallback generated from screen name and prompt.",
        )
}
