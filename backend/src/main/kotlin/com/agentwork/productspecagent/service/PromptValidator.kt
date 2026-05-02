package com.agentwork.productspecagent.service

sealed class PromptValidator {
    abstract fun validate(content: String): List<String>

    data object NotBlank : PromptValidator() {
        override fun validate(content: String): List<String> =
            if (content.isBlank()) listOf("Inhalt darf nicht leer sein.") else emptyList()
    }

    data class MaxLength(val max: Int) : PromptValidator() {
        override fun validate(content: String): List<String> =
            if (content.length > max) listOf("Maximal $max Zeichen erlaubt (aktuell ${content.length}).") else emptyList()
    }

    data class RequiresAll(val tokens: List<String>, val reason: String) : PromptValidator() {
        override fun validate(content: String): List<String> {
            val missing = tokens.filter { !content.contains(it) }
            return if (missing.isEmpty()) emptyList()
            else listOf("Fehlende Marker: ${missing.joinToString(", ")}. $reason")
        }
    }
}
