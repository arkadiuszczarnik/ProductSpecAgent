package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.DesignColor
import com.agentwork.productspecagent.domain.DesignComponentSignal
import com.agentwork.productspecagent.domain.DesignImageAnalysis
import com.agentwork.productspecagent.domain.DesignImageInput
import com.agentwork.productspecagent.domain.DesignLayoutRegion
import com.agentwork.productspecagent.domain.DesignTypographySignal
import com.agentwork.productspecagent.service.PromptService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component

class InvalidDesignImageAnalysisException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class DesignImageAnalysisInput(
    val projectId: String,
    val image: DesignImageInput,
    val imageBytes: ByteArray,
)

@Serializable
data class DesignImageAnalysisResult(
    val analysis: DesignImageAnalysis,
)

@Component
open class DesignImageAnalysisAgent(
    private val koogRunner: KoogAgentRunner? = null,
    private val promptService: PromptService? = null,
) {
    companion object {
        const val AGENT_ID = "design-image-analysis"
        private const val PROMPT_ID = "design-image-analysis-system"
        private const val MAX_PALETTE = 8
        private const val MAX_TYPOGRAPHY = 6
        private const val MAX_LAYOUT = 8
        private const val MAX_COMPONENTS = 10
        private const val MAX_TAGS = 8
        private const val MAX_SIGNALS = 8
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val hexRegex = Regex("^#[0-9a-fA-F]{6}$")

    open fun analyze(input: DesignImageAnalysisInput): DesignImageAnalysisResult {
        val prompt = buildPrompt(input)
        val raw = runBlocking {
            runImageAgent(
                systemPrompt = promptService?.get(PROMPT_ID) ?: defaultSystemPrompt(),
                prompt = prompt,
                imageBytes = input.imageBytes,
                contentType = input.image.contentType,
                fileName = input.image.originalName,
            )
        }
        return if (raw == null) {
            fallback(input)
        } else {
            parseResponse(raw)
        }
    }

    protected open suspend fun runImageAgent(
        systemPrompt: String,
        prompt: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
    ): String? = koogRunner?.runWithImage(AGENT_ID, systemPrompt, prompt, imageBytes, contentType, fileName)

    private fun parseResponse(raw: String): DesignImageAnalysisResult {
        val jsonStr = raw.replace("```json", "").replace("```", "").trim()
        val parsed = try {
            json.decodeFromString<DesignImageAnalysisResult>(jsonStr)
        } catch (ex: SerializationException) {
            throw InvalidDesignImageAnalysisException("Invalid design image analysis JSON", ex)
        } catch (ex: IllegalArgumentException) {
            throw InvalidDesignImageAnalysisException("Invalid design image analysis JSON", ex)
        }
        return DesignImageAnalysisResult(normalize(parsed.analysis))
    }

    private fun normalize(analysis: DesignImageAnalysis): DesignImageAnalysis {
        val summary = analysis.summary.clean().ifBlank { "Design image analysis unavailable." }
        return DesignImageAnalysis(
            summary = summary,
            palette = analysis.palette.take(MAX_PALETTE).map {
                DesignColor(
                    hex = normalizeHex(it.hex),
                    role = it.role.clean(),
                    weight = it.weight.clean(),
                    notes = it.notes.clean(),
                )
            },
            typography = analysis.typography.take(MAX_TYPOGRAPHY).map {
                DesignTypographySignal(
                    category = it.category.clean(),
                    role = it.role.clean(),
                    weight = it.weight.clean(),
                    notes = it.notes.clean(),
                )
            },
            layoutHierarchy = analysis.layoutHierarchy.take(MAX_LAYOUT).map {
                DesignLayoutRegion(
                    name = it.name.clean(),
                    order = it.order,
                    priority = it.priority,
                    description = it.description.clean(),
                )
            },
            components = analysis.components.take(MAX_COMPONENTS).map {
                DesignComponentSignal(
                    name = it.name.clean(),
                    role = it.role.clean(),
                    description = it.description.clean(),
                )
            },
            moodTags = analysis.moodTags
                .map { it.clean().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_TAGS),
            brandSignals = analysis.brandSignals
                .map { it.clean() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(MAX_SIGNALS),
            designBrief = analysis.designBrief.clean().ifBlank { summary },
        )
    }

    private fun fallback(input: DesignImageAnalysisInput): DesignImageAnalysisResult {
        val image = input.image
        return DesignImageAnalysisResult(
            analysis = DesignImageAnalysis(
                summary = "Metadata-only image reference: ${image.originalName} (${image.contentType}, ${image.sizeBytes} bytes). This fallback uses upload metadata only.",
                palette = emptyList(),
                typography = emptyList(),
                layoutHierarchy = emptyList(),
                components = emptyList(),
                moodTags = listOf("metadata-only", "reference-image"),
                brandSignals = listOf(
                    "Source file ${image.originalName}",
                    "Content type ${image.contentType}",
                ),
                designBrief = "Use ${image.originalName} as a reference image placeholder; fallback does not inspect pixels or infer visual details.",
            ),
        )
    }

    private fun buildPrompt(input: DesignImageAnalysisInput): String = buildString {
        appendLine("Project ID: ${input.projectId}")
        appendLine()
        appendLine("Image metadata:")
        appendLine("Name: ${input.image.originalName}")
        appendLine("Content type: ${input.image.contentType}")
        appendLine("Size bytes: ${input.image.sizeBytes}")
        appendLine("Stored reference: ${input.image.contentRef}")
        appendLine("Uploaded at: ${input.image.uploadedAt}")
        appendLine()
        appendLine("Return exactly this JSON shape without markdown:")
        appendLine(
            """{"analysis":{"summary":"...","palette":[{"hex":"#RRGGBB","role":"...","weight":"...","notes":"..."}],"typography":[{"category":"...","role":"...","weight":"...","notes":"..."}],"layoutHierarchy":[{"name":"...","order":1,"priority":1,"description":"..."}],"components":[{"name":"...","role":"...","description":"..."}],"moodTags":["..."],"brandSignals":["..."],"designBrief":"..."}}""",
        )
    }

    private fun defaultSystemPrompt(): String =
        "Analyze the uploaded design image for reusable product UI direction. Return only valid JSON matching the requested shape. " +
            "Do not identify people, infer sensitive traits, or include markdown."

    private fun normalizeHex(value: String): String {
        val trimmed = value.trim()
        return if (hexRegex.matches(trimmed)) trimmed.uppercase() else "#000000"
    }

    private fun String.clean(): String = trim()
}
