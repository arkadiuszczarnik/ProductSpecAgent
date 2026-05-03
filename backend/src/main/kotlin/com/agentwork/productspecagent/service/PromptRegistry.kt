package com.agentwork.productspecagent.service

import org.springframework.stereotype.Component

data class PromptDefinition(
    val id: String,
    val title: String,
    val description: String,
    val agent: String,
    val resourcePath: String,
    val validators: List<PromptValidator> = emptyList(),
)

class PromptNotFoundException(id: String) : RuntimeException("Prompt not found: $id")

@Component
class PromptRegistry {
    val definitions: List<PromptDefinition> = listOf(
        PromptDefinition(
            id = "idea-base",
            title = "IdeaToSpec — Basis-System-Prompt",
            description = "Rolle und Schritt-Reihenfolge des IdeaToSpec-Agents. Wird bei jedem Wizard-Schritt vor den Step-Prompt gehängt.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-base.md",
            validators = listOf(
                PromptValidator.NotBlank,
                PromptValidator.MaxLength(100_000),
                PromptValidator.RequiresAll(
                    tokens = listOf("[STEP_COMPLETE]"),
                    reason = "Dieser Marker treibt die Wizard-Progression — ohne Erwähnung kann der Agent keinen Step abschließen.",
                ),
            ),
        ),
        PromptDefinition(
            id = "idea-marker-reminder",
            title = "IdeaToSpec — Marker-Erinnerung",
            description = "Wird an Decision/Clarification-Feedback-Prompts angehängt, um den Agent an die Marker-Tokens zu erinnern.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-marker-reminder.md",
            validators = listOf(
                PromptValidator.NotBlank,
                PromptValidator.MaxLength(50_000),
                PromptValidator.RequiresAll(
                    tokens = listOf("[STEP_COMPLETE]", "[DECISION_NEEDED]", "[CLARIFICATION_NEEDED]"),
                    reason = "Die Erinnerung muss alle drei Marker erklären — sonst funktioniert der Marker-Parser nicht.",
                ),
            ),
        ),
        PromptDefinition(
            id = "idea-step-IDEA",
            title = "IdeaToSpec — Step IDEA",
            description = "Step-spezifische Anweisung für den IDEA-Schritt. Wird zwischen Basis-Prompt und Locale-Anweisung eingefügt.",
            agent = "IdeaToSpec",
            resourcePath = "/prompts/idea-step-IDEA.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "decision-system",
            title = "Decision — System-Prompt",
            description = "Rolle des Decision-Agents (strukturierte Entscheidungs-Optionen als JSON).",
            agent = "Decision",
            resourcePath = "/prompts/decision-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "plan-system",
            title = "Plan — System-Prompt",
            description = "Rolle des Plan-Generators (Epics/Stories/Tasks als JSON).",
            agent = "Plan",
            resourcePath = "/prompts/plan-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
        PromptDefinition(
            id = "feature-proposal-system",
            title = "Feature-Proposal — System-Prompt",
            description = "Rolle des Feature-Proposal-Agents (Feature-Graph-Vorschlag basierend auf Spec + Uploads).",
            agent = "FeatureProposal",
            resourcePath = "/prompts/feature-proposal-system.md",
            validators = listOf(PromptValidator.NotBlank, PromptValidator.MaxLength(50_000)),
        ),
    )

    fun byId(id: String): PromptDefinition =
        definitions.find { it.id == id } ?: throw PromptNotFoundException(id)
}
