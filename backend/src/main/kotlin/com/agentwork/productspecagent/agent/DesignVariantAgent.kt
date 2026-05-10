package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignAnalysis
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.service.PromptService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

@Serializable
data class DesignGenerationInput(
    val projectId: String,
    val description: String?,
    val image: DesignImageInput?,
)

@Serializable
data class DesignGenerationResult(
    val analysis: DesignAnalysis,
    val title: String,
    val html: String,
    val rationale: String,
)

@Component
open class DesignVariantAgent(
    private val koogRunner: KoogAgentRunner? = null,
    private val promptService: PromptService? = null,
) {
    companion object {
        const val AGENT_ID = "design-variant"
    }

    private val json = Json { ignoreUnknownKeys = true }

    open fun generate(input: DesignGenerationInput): DesignGenerationResult {
        val prompt = buildPrompt(input)
        val raw = runBlocking { runAgent(prompt) }
        if (raw != null) {
            runCatching { return parseResponse(raw) }
        }
        return fallback(input)
    }

    protected open suspend fun runAgent(prompt: String): String? =
        koogRunner?.run(AGENT_ID, promptService?.get("design-variant-system") ?: defaultSystemPrompt(), prompt)

    private fun parseResponse(raw: String): DesignGenerationResult {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = json.decodeFromString<DesignGenerationResult>(jsonStr)
        return parsed.copy(
            title = parsed.title.trim().ifBlank { "Generated Design" },
            html = parsed.html.trim(),
            rationale = parsed.rationale.trim().ifBlank { "Generated from current design input." },
        )
    }

    private fun fallback(input: DesignGenerationInput): DesignGenerationResult {
        val subject = input.description?.take(120)
            ?: input.image?.originalName?.let { "Reference image $it" }
            ?: "Generated design"
        val escapedSubject = escapeHtml(subject)
        val imageLine = input.image?.originalName
            ?.let { "<p>Reference image: ${escapeHtml(it)}</p>" }
            .orEmpty()
        val source = when {
            input.description != null && input.image != null -> "description and image"
            input.image != null -> "image"
            else -> "description"
        }

        return DesignGenerationResult(
            analysis = DesignAnalysis(
                summary = "Generated layout from $source.",
                visualDirection = "Clean product interface with clear hierarchy.",
                rationale = "Fallback generation keeps local development deterministic.",
            ),
            title = "Generated Design",
            html = """
                <!doctype html>
                <html>
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <style>
                      body{margin:0;font-family:system-ui;background:#f7f7f4;color:#171717}
                      main{min-height:100vh;display:grid;place-items:center;padding:48px}
                      section{max-width:920px;width:100%;background:white;border:1px solid #ddd;border-radius:8px;padding:40px}
                      h1{font-size:40px;line-height:1.05;margin:0 0 16px}
                      p{font-size:16px;line-height:1.6;color:#4b5563}
                    </style>
                  </head>
                  <body>
                    <main><section><h1>$escapedSubject</h1><p>Generated HTML layout preview.</p>$imageLine</section></main>
                  </body>
                </html>
            """.trimIndent(),
            rationale = "Fallback generated from the current V1 input.",
        )
    }

    private fun buildPrompt(input: DesignGenerationInput): String = buildString {
        appendLine("Project ID: ${input.projectId}")
        appendLine()
        appendLine("Description:")
        appendLine(input.description?.takeIf { it.isNotBlank() } ?: "No written description provided.")
        appendLine()
        appendLine("Reference image:")
        if (input.image != null) {
            appendLine("Name: ${input.image.originalName}")
            appendLine("Content type: ${input.image.contentType}")
            appendLine("Size bytes: ${input.image.sizeBytes}")
            appendLine("Stored reference: ${input.image.contentRef}")
        } else {
            appendLine("No image provided.")
        }
        appendLine()
        appendLine("Return exactly this JSON shape without markdown:")
        appendLine(
            """{"analysis":{"summary":"...","visualDirection":"...","rationale":"..."},"title":"...","html":"<!doctype html>...","rationale":"..."}""",
        )
    }

    private fun defaultSystemPrompt(): String =
        "You generate one secure standalone HTML layout preview from a product design description and optional image metadata. " +
            "Return only valid JSON with analysis, title, html, and rationale. Do not include external URLs."

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
