package com.agentwork.productspecagent.agent

import com.agentwork.productspecagent.domain.*
import com.agentwork.productspecagent.service.ClarificationService
import com.agentwork.productspecagent.service.DecisionService
import com.agentwork.productspecagent.service.ProjectService
import com.agentwork.productspecagent.service.TaskService
import com.agentwork.productspecagent.service.WizardFeatureInput
import com.agentwork.productspecagent.service.WizardService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
open class IdeaToSpecAgent(
    private val contextBuilder: SpecContextBuilder,
    private val projectService: ProjectService,
    @Value("\${agent.system-prompt}") private val baseSystemPrompt: String,
    private val decisionService: DecisionService,
    private val clarificationService: ClarificationService,
    private val wizardService: WizardService,
    private val taskService: TaskService,
    private val koogRunner: KoogAgentRunner? = null
) {

    private val logger = LoggerFactory.getLogger(IdeaToSpecAgent::class.java)
    private val stepOrder = FlowStepType.entries.toList()

    suspend fun chat(projectId: String, userMessage: String, locale: String = "en"): ChatResponse {
        val flowState = projectService.getFlowState(projectId)
        val context = contextBuilder.buildContext(projectId)

        val currentStep = flowState.currentStep

        val localeInstruction = buildLocaleInstruction(locale)
        val stepPrompt = buildStepPrompt(currentStep)
        val systemPromptWithContext = "$baseSystemPrompt\n\n$stepPrompt\n\n$localeInstruction\n\n$context"

        val rawResponse = runAgent(systemPromptWithContext, userMessage)

        val stepCompleted = rawResponse.contains("[STEP_COMPLETE]")
        val summaryMatch = Regex("""\[STEP_SUMMARY]\s*[:：]\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
            .find(rawResponse)
        val summaryContent = summaryMatch?.groupValues?.get(1)?.trim()

        val decisionTitle = extractDecisionTitle(rawResponse)
        val clarification = extractClarification(rawResponse)
        val clarificationQuestion = clarification?.first
        val clarificationReason = clarification?.second

        logger.info("chat() markers – decision={}, clarification={}", decisionTitle, clarificationQuestion)

        val cleanMessage = cleanMarkers(rawResponse)

        var nextStep = currentStep
        var flowStateChanged = false
        var createdDecisionId: String? = null

        if (decisionTitle != null) {
            val decision = decisionService.createDecision(projectId, decisionTitle, currentStep)
            createdDecisionId = decision.id
        }

        var createdClarificationId: String? = null

        if (clarificationQuestion != null && clarificationReason != null) {
            val clarification = clarificationService.createClarification(
                projectId, clarificationQuestion, clarificationReason, currentStep
            )
            createdClarificationId = clarification.id
        }

        if (stepCompleted) {
            val fileName = currentStep.name.lowercase() + ".md"
            val title = currentStep.name.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            val markdownContent = "# $title\n\n${summaryContent ?: cleanMessage}"
            projectService.saveSpecFile(projectId, fileName, markdownContent)

            val now = Instant.now().toString()
            val updatedSteps = flowState.steps.map { step ->
                when (step.stepType) {
                    currentStep -> step.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
                    else -> step
                }
            }

            val currentIndex = stepOrder.indexOf(currentStep)
            if (currentIndex + 1 < stepOrder.size) {
                nextStep = stepOrder[currentIndex + 1]
                val finalSteps = updatedSteps.map { step ->
                    if (step.stepType == nextStep) step.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
                    else step
                }
                projectService.updateFlowState(projectId, flowState.copy(
                    steps = finalSteps, currentStep = nextStep
                ))
            } else {
                projectService.updateFlowState(projectId, flowState.copy(steps = updatedSteps))
            }
            flowStateChanged = true
        }

        return ChatResponse(
            message = cleanMessage,
            flowStateChanged = flowStateChanged,
            currentStep = nextStep.name,
            decisionId = createdDecisionId,
            clarificationId = createdClarificationId
        )
    }

    suspend fun processWizardStep(
        projectId: String,
        step: String,
        fields: Map<String, Any>,
        locale: String = "en"
    ): WizardStepCompleteResponse {
        val wizardData = wizardService.getWizardData(projectId)
        val existingDecisions = decisionService.listDecisions(projectId)
        val existingClarifications = clarificationService.listClarifications(projectId)
        val wizardContext = contextBuilder.buildWizardContext(
            wizardData, step, fields, existingDecisions, existingClarifications
        )

        val prompt = buildWizardStepFeedbackPrompt(step, fields)

        val currentStepType = try { FlowStepType.valueOf(step) } catch (_: Exception) { null }
        val stepPrompt = if (currentStepType != null) buildStepPrompt(currentStepType) else ""
        val localeInstruction = buildLocaleInstruction(locale)
        val systemPromptWithContext = "$baseSystemPrompt\n\n$stepPrompt\n\n$localeInstruction\n\n$wizardContext"

        val rawResponse = runAgent(systemPromptWithContext, prompt)

        val decisionTitle = extractDecisionTitle(rawResponse)
        val clarification = extractClarification(rawResponse)
        val clarificationQuestion = clarification?.first
        val clarificationReason = clarification?.second

        logger.info("processWizardStep({}) markers – decision={}, clarification={}", step, decisionTitle, clarificationQuestion)

        val cleanMessage = cleanMarkers(rawResponse)

        var createdDecisionId: String? = null
        if (decisionTitle != null && currentStepType != null) {
            val decision = decisionService.createDecision(projectId, decisionTitle, currentStepType)
            createdDecisionId = decision.id
        }

        var createdClarificationId: String? = null
        if (clarificationQuestion != null && clarificationReason != null && currentStepType != null) {
            val clarification = clarificationService.createClarification(
                projectId, clarificationQuestion, clarificationReason, currentStepType
            )
            createdClarificationId = clarification.id
        }

        // Determine next step
        val isLastStep = currentStepType != null && stepOrder.indexOf(currentStepType) == stepOrder.size - 1

        // Feature 18 (Step-Blocker Gate): do NOT advance the flow state if this step
        // has open blockers. A blocker can be either freshly created by the agent in this
        // call (decision/clarification marker) OR already existing as PENDING/OPEN in storage.
        val hasNewBlocker = createdDecisionId != null || createdClarificationId != null
        val hasExistingBlocker = currentStepType != null && (
            existingDecisions.any {
                it.stepType == currentStepType && it.status == DecisionStatus.PENDING
            } || existingClarifications.any {
                it.stepType == currentStepType && it.status == ClarificationStatus.OPEN
            }
        )
        val isBlocked = hasNewBlocker || hasExistingBlocker

        val nextStepType = if (currentStepType != null && !isLastStep && !isBlocked) {
            val idx = stepOrder.indexOf(currentStepType)
            if (idx + 1 < stepOrder.size) stepOrder[idx + 1] else null
        } else {
            null
        }

        // Update flow state
        if (currentStepType != null) {
            val flowState = projectService.getFlowState(projectId)
            val now = java.time.Instant.now().toString()
            val updatedSteps = flowState.steps.map { s ->
                when {
                    // Blocked: keep current step IN_PROGRESS, do not mark COMPLETED
                    isBlocked && s.stepType == currentStepType ->
                        s.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
                    s.stepType == currentStepType ->
                        s.copy(status = FlowStepStatus.COMPLETED, updatedAt = now)
                    s.stepType == nextStepType ->
                        s.copy(status = FlowStepStatus.IN_PROGRESS, updatedAt = now)
                    else -> s
                }
            }
            val newFlowState = flowState.copy(
                steps = updatedSteps,
                currentStep = if (isBlocked) currentStepType else (nextStepType ?: currentStepType)
            )
            projectService.updateFlowState(projectId, newFlowState)

            // Save spec file (always — the user's input is valid, only progression is gated)
            val fileName = step.lowercase() + ".md"
            val title = step.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            val fieldsMarkdown = fields.entries.joinToString("\n") { "- **${it.key}**: ${it.value}" }
            val markdownContent = "# $title\n\n$fieldsMarkdown"

            // Feature 21 (Wizard FEATURES -> EPIC tasks): If the user just completed the
            // FEATURES wizard step without blockers, derive EPIC + Stories + Tasks per
            // feature via the PlanGeneratorAgent. Must run BEFORE saveSpecFile, because
            // saveSpecFile triggers regenerateDocsScaffold which reads tasks to build
            // docs/features/.
            if (!isBlocked && currentStepType == FlowStepType.FEATURES) {
                // FEATURE-22-TODO Task 6: resolve category from project
                val wizardFeatures = parseWizardFeatures(fields, category = null)
                if (wizardFeatures.isNotEmpty()) {
                    try {
                        taskService.replaceWizardFeatureTasks(projectId, wizardFeatures)
                    } catch (e: Exception) {
                        logger.warn("Failed to derive tasks from wizard features for project {}: {}", projectId, e.message)
                    }
                }
            }

            projectService.saveSpecFile(projectId, fileName, markdownContent)
        }

        // Generate spec summary on last step (only if not blocked)
        if (isLastStep && !isBlocked && currentStepType != null) {
            val allWizardData = wizardService.getWizardData(projectId)
            val fullContext = contextBuilder.buildWizardContext(allWizardData, step, fields)
            val summaryPrompt = buildString {
                appendLine("Based on all the information gathered in the wizard steps below, generate a complete product specification summary in markdown format.")
                appendLine("Include sections for: Product Overview, Problem, Target Audience, Scope, MVP, and any technical decisions made.")
                appendLine()
                appendLine(fullContext)
            }
            val localeInstruction = buildLocaleInstruction(locale)
            val summarySystemPrompt = "$baseSystemPrompt\n\n$localeInstruction"
            val specContent = runAgent(summarySystemPrompt, summaryPrompt)
            projectService.saveSpecFile(projectId, "spec.md", specContent)
        }

        val exportTriggered = isLastStep && !isBlocked

        return WizardStepCompleteResponse(
            message = cleanMessage,
            nextStep = nextStepType?.name,
            exportTriggered = exportTriggered,
            decisionId = createdDecisionId,
            clarificationId = createdClarificationId
        )
    }

    private fun buildWizardStepFeedbackPrompt(step: String, fields: Map<String, Any>): String {
        val fieldsDescription = fields.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
        return when (step) {
            "IDEA" -> buildString {
                appendLine("The user just completed the IDEA wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("IMPORTANT: This is the IDEA step. Focus ONLY on the idea itself. Do NOT discuss problem statement, target audience, value proposition, or technical details – these are handled in later steps (PROBLEM, TARGET_AUDIENCE, SCOPE, etc.).")
                appendLine()
                appendLine("Analyze ONLY the idea:")
                appendLine("1. Is the product idea clearly described? Can you understand what the product is supposed to DO?")
                appendLine("2. Does the chosen category fit?")
                appendLine("3. Is the product name clear and fitting?")
                appendLine("4. Is the vision concrete enough to work with, or is it too vague?")
                appendLine()
                appendLine("If the vision is too vague (e.g. just a few words), ask the user to elaborate on WHAT the product does – not WHY or for WHOM (those come later).")
                appendLine("If the idea is clear enough, acknowledge it and confirm it as a solid starting point.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
            "PROBLEM" -> buildString {
                appendLine("The user just completed the PROBLEM wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the problem definition:")
                appendLine("1. Is the core problem clearly defined and specific enough?")
                appendLine("2. Are the current solutions and their shortcomings well understood?")
                appendLine("3. Are the pain points concrete and measurable?")
                appendLine()
                appendLine("If there are contradictions or missing aspects, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
            "TARGET_AUDIENCE" -> buildString {
                appendLine("The user just completed the TARGET_AUDIENCE wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the target audience definition:")
                appendLine("1. Is the primary audience clearly defined?")
                appendLine("2. Are user needs specific and actionable?")
                appendLine("3. Is there a potential conflict between primary and secondary audiences?")
                appendLine()
                appendLine("If the audience is too broad or there is a strategic choice to make (e.g., B2B vs B2C), use [DECISION_NEEDED].")
                appendLine("If important details are missing, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
            "SCOPE" -> buildString {
                appendLine("The user just completed the SCOPE wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the scope definition:")
                appendLine("1. Is the boundary between in-scope and out-of-scope clear?")
                appendLine("2. Are the constraints realistic?")
                appendLine("3. Is the scope appropriate for the stated timeline and budget?")
                appendLine()
                appendLine("If a scope trade-off needs user input (e.g., reduce features vs. extend timeline), use [DECISION_NEEDED].")
                appendLine("If constraints are unclear or contradictory, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
            "ARCHITECTURE" -> buildString {
                appendLine("The user just completed the ARCHITECTURE wizard step with the following input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Analyze the architecture:")
                appendLine("1. Does the tech stack fit the requirements and team skills?")
                appendLine("2. Is the system design appropriate for the scale?")
                appendLine("3. Are there important architectural trade-offs to consider?")
                appendLine()
                appendLine("If there is a major architectural choice (e.g., monolith vs. microservices, SQL vs. NoSQL), use [DECISION_NEEDED].")
                appendLine("If details are unclear, use [CLARIFICATION_NEEDED].")
                appendLine("Be encouraging and constructive.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
            else -> buildString {
                appendLine("The user just completed wizard step: $step")
                appendLine("Their input:")
                appendLine(fieldsDescription)
                appendLine()
                appendLine("Please provide brief, helpful feedback about their input for this step.")
                appendLine("Be encouraging and mention any suggestions for improvement if applicable.")
                appendLine()
                appendLine(MARKER_REMINDER)
            }
        }
    }

    private fun buildStepPrompt(step: FlowStepType): String = when (step) {
        FlowStepType.IDEA -> IDEA_STEP_PROMPT
        else -> ""
    }

    private fun buildLocaleInstruction(locale: String): String {
        val langCode = locale.split("-", "_").first().lowercase()
        val languageName = mapOf(
            "de" to "Deutsch", "en" to "English", "fr" to "Français",
            "es" to "Español", "it" to "Italiano", "pt" to "Português",
            "nl" to "Nederlands", "pl" to "Polski", "ja" to "日本語",
            "zh" to "中文", "ko" to "한국語", "ru" to "Русский"
        )[langCode]

        return if (languageName != null) {
            "IMPORTANT: Always respond in $languageName ($langCode). Do not switch languages."
        } else {
            "IMPORTANT: Always respond in the language with code '$langCode'. Do not switch languages."
        }
    }

    /**
     * Extracts a decision title from the raw response.
     * Handles variants: [DECISION_NEEDED]: ..., **[DECISION_NEEDED]**: ..., `[DECISION_NEEDED]`: ...
     */
    fun extractDecisionTitle(raw: String): String? {
        val pattern = Regex("""\[DECISION_NEEDED]\s*[:：]\s*(.+)""")
        // Also try markdown-escaped variants
        val markdownPattern = Regex("""\*?\*?\[DECISION_NEEDED]\*?\*?\s*[:：]\s*(.+)""")
        val match = pattern.find(raw) ?: markdownPattern.find(raw)
        return match?.groupValues?.get(1)?.trim()?.removeSurrounding("**")?.removeSurrounding("`")
    }

    /**
     * Extracts a clarification question and reason from the raw response.
     * Handles variants with markdown formatting and different separator styles.
     */
    fun extractClarification(raw: String): Pair<String, String>? {
        // Standard: [CLARIFICATION_NEEDED]: question | reason
        val pattern = Regex("""\[CLARIFICATION_NEEDED]\s*[:：]\s*([^|]+)\|\s*(.+)""")
        val markdownPattern = Regex("""\*?\*?\[CLARIFICATION_NEEDED]\*?\*?\s*[:：]\s*([^|]+)\|\s*(.+)""")
        val match = pattern.find(raw) ?: markdownPattern.find(raw)
        if (match != null) {
            val question = match.groupValues[1].trim().removeSurrounding("**").removeSurrounding("`")
            val reason = match.groupValues[2].trim().removeSurrounding("**").removeSurrounding("`")
            return Pair(question, reason)
        }
        // Fallback: [CLARIFICATION_NEEDED]: question (without pipe separator – use whole text as question)
        val fallbackPattern = Regex("""\[CLARIFICATION_NEEDED]\s*[:：]\s*(.+)""")
        val fallbackMatch = fallbackPattern.find(raw)
        if (fallbackMatch != null) {
            val text = fallbackMatch.groupValues[1].trim()
            return Pair(text, "Klärung erforderlich um fortfahren zu können")
        }
        return null
    }

    /**
     * Removes all marker lines from the response for clean display.
     */
    fun cleanMarkers(raw: String): String {
        return raw
            .replace("[STEP_COMPLETE]", "")
            .replace(Regex("""\*?\*?\[STEP_SUMMARY]\*?\*?\s*[:：][^\n]*"""), "")
            .replace(Regex("""\*?\*?\[DECISION_NEEDED]\*?\*?\s*[:：][^\n]*"""), "")
            .replace(Regex("""\*?\*?\[CLARIFICATION_NEEDED]\*?\*?\s*[:：][^\n]*"""), "")
            .trim()
    }

    protected open suspend fun runAgent(systemPrompt: String, userMessage: String): String {
        val result = koogRunner?.run(systemPrompt, userMessage)
            ?: throw UnsupportedOperationException("KoogAgentRunner not configured.")
        logger.info("Agent raw response (last 500 chars): ...{}", result.takeLast(500))
        return result
    }

    companion object {
        /**
         * Parses wizard-FEATURES step input into a normalized list of [WizardFeatureInput].
         *
         * Accepts either:
         *  - Graph form: a `Map` with keys `"features"` (List<Map>) and optional `"edges"` (List<Map>).
         *  - Legacy flat form: a `List` of feature maps (or raw strings treated as titles).
         *  - `null` or unrecognized types → empty list.
         *
         * [category] drives the default `scopes` when a feature does not declare them (SaaS/Mobile/Desktop → FRONTEND+BACKEND;
         * API/CLI → BACKEND only; Library → empty set; otherwise FRONTEND+BACKEND).
         */
        fun parseWizardFeatures(raw: Any?, category: String?): List<WizardFeatureInput> {
            val defaultScopes = defaultScopesFor(category)
            val featuresRaw: List<Any?>
            val edgesRaw: List<Any?>

            when (raw) {
                is Map<*, *> -> {
                    featuresRaw = (raw["features"] as? List<*>) ?: emptyList<Any>()
                    edgesRaw = (raw["edges"] as? List<*>) ?: emptyList<Any>()
                }
                is List<*> -> {
                    featuresRaw = raw
                    edgesRaw = emptyList<Any>()
                }
                else -> return emptyList()
            }

            val dependsByTarget = mutableMapOf<String, MutableList<String>>()
            for (e in edgesRaw) {
                val m = e as? Map<*, *> ?: continue
                val from = m["from"]?.toString() ?: continue
                val to = m["to"]?.toString() ?: continue
                dependsByTarget.getOrPut(to) { mutableListOf() }.add(from)
            }

            val result = mutableListOf<WizardFeatureInput>()
            for (f in featuresRaw) {
                val m = when (f) {
                    is Map<*, *> -> f
                    is String -> mapOf("title" to f)
                    else -> continue
                }
                val title = (m["title"] ?: m["name"])?.toString()?.trim()
                if (title.isNullOrBlank()) continue
                val id = m["id"]?.toString()?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
                val description = (m["description"] ?: m["desc"])?.toString() ?: ""
                val scopes = parseScopes(m["scopes"], defaultScopes)
                val scopeFields: Map<String, String> = (m["scopeFields"] as? Map<*, *>)
                    ?.mapNotNull { (k, v) -> if (k is String && v != null) k to v.toString() else null }
                    ?.toMap()
                    ?: emptyMap()
                result.add(WizardFeatureInput(
                    id = id,
                    title = title,
                    description = description,
                    scopes = scopes,
                    scopeFields = scopeFields,
                    dependsOn = dependsByTarget[id] ?: emptyList(),
                ))
            }
            return result
        }

        private fun parseScopes(raw: Any?, fallback: Set<FeatureScope>): Set<FeatureScope> {
            val list = raw as? List<*> ?: return fallback
            return list.mapNotNull { s ->
                runCatching { FeatureScope.valueOf(s.toString().uppercase()) }.getOrNull()
            }.toSet().ifEmpty { fallback }
        }

        private fun defaultScopesFor(category: String?): Set<FeatureScope> = when (category) {
            "SaaS", "Mobile App", "Desktop App" -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
            "API", "CLI Tool" -> setOf(FeatureScope.BACKEND)
            "Library" -> emptySet()
            else -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        }

        const val MARKER_REMINDER = """
OUTPUT REQUIREMENT:
After your feedback text, you MAY end your response with one of these markers on its own line:
- [DECISION_NEEDED]: <short title> — when the user faces a strategic choice between 2-3 options
- [CLARIFICATION_NEEDED]: <question> | <why this matters> — when important information is missing, vague, or contradictory

CRITICAL RULES FOR MARKERS:
1. If the "PREVIOUS CLARIFICATIONS & DECISIONS FOR THIS STEP" section already lists a clarification or decision that covers your concern (answered OR still open), you MUST NOT emit a new marker for the same or similar topic. Treat answered items as confirmed facts and build on them.
2. Only emit a marker if there is a GENUINELY NEW, distinct gap that is not covered by any previous item.
3. If all important questions are already covered or answered, OMIT the marker entirely and just give a short acknowledgement. This is the expected case after the first round of clarification.
4. Do not invent new concerns just to emit a marker. Silence is a valid answer.

Example response ending (no new marker because previous clarifications already cover the topic):
---
Danke für die Ergänzung. Mit der beantworteten Klärung ist die Idee jetzt ausreichend klar und wir können zum nächsten Schritt.
---

Example response ending (new marker for a genuinely new concern):
---
Deine Idee ist ein guter Ausgangspunkt! Allerdings ist noch unklar, wer genau die Zielgruppe ist.

[CLARIFICATION_NEEDED]: Wer ist die primaere Zielgruppe – Entwickler oder Nicht-Techniker? | Die Zielgruppe bestimmt die gesamte UX-Richtung und das Feature-Set grundlegend.
---"""

        val IDEA_STEP_PROMPT = """
=== IDEA STEP INSTRUCTIONS ===

Du bist ein erfahrener Produktberater. In diesem Schritt geht es NUR darum, die Produktidee selbst klar zu formulieren.

WICHTIG – ABGRENZUNG:
- Sprich NICHT über Problemstellung, Zielgruppe, Nutzerwert, Pricing oder technische Details.
- Diese Themen werden in späteren Schritten behandelt (PROBLEM, TARGET_AUDIENCE, SCOPE, MVP, etc.).
- Auch wenn die Idee vage ist: bleibe beim Thema "Was ist das Produkt? Was soll es tun?"

## Dein Vorgehen:

### Phase 1: Kontext verstehen
- Prüfe, welche Informationen bereits vorhanden sind (Produktname, Kategorie, Beschreibung)
- Verstehe die Ausgangsidee und den Rahmen
- Wenn das Vorhaben zu breit ist, hilf bei der Zerlegung und fokussiere auf den Kern

### Phase 2: Idee schärfen
- Wenn die Beschreibung zu kurz oder vage ist, frage nach:
  - Was genau soll das Produkt tun? (Hauptfunktion)
  - Wie soll es funktionieren? (grobe Vorstellung, nicht technisch)
  - Was macht es anders als Bestehendes?
- Stelle immer nur EINE Frage pro Nachricht

### Phase 3: Produktrichtungen vorschlagen (wenn nötig)
- Falls die Idee mehrere Richtungen erlaubt, stelle 2–3 mögliche Produktrichtungen vor
- Beschreibe jeweils kurz, was das Produkt in dieser Variante wäre
- NICHT in Spezifikation oder Umsetzung abrutschen

### Phase 4: Idee bestätigen
Sobald die Idee klar genug ist, fasse sie zusammen:
- Produktname und Kategorie
- Was das Produkt tut (1-2 Sätze)
- Grobe Richtung / Ansatz
- Hole Bestätigung ein

Erst wenn der Nutzer bestätigt hat, markiere mit [STEP_COMPLETE].

## Kommunikationsregeln:
- Sei ermutigend und konstruktiv
- Fasse dich präzise
- Nutze vorhandene Wizard-Daten als Ausgangspunkt statt sie erneut abzufragen
- Wenn die Vision nur wenige Worte enthält, frage KONKRET nach was das Produkt tun soll

## Markers im IDEA-Schritt:
- Nutze [CLARIFICATION_NEEDED] wenn die Produktidee zu vage ist und du nicht verstehst, was das Produkt tun soll.
  Beispiel: [CLARIFICATION_NEEDED]: Was genau soll ProgrammAgent tun – bestehenden Code kompilieren, oder neuen Code aus Beschreibungen generieren? | Die Grundfunktion des Produkts muss klar sein bevor wir weitermachen koennen.
- Nutze [DECISION_NEEDED] wenn es 2-3 verschiedene Produktrichtungen gibt.
  Beispiel: [DECISION_NEEDED]: Produktrichtung wählen (Codegenerator vs. Build-Automatisierung vs. No-Code-Plattform)

=== END IDEA STEP INSTRUCTIONS ===
        """.trimIndent()
    }
}
