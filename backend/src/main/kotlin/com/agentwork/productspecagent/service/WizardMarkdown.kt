package com.agentwork.productspecagent.service

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

object WizardMarkdown {
    fun renderStep(stepName: String, fields: Map<String, JsonElement>): String? {
        if (fields.isEmpty()) return null

        if (stepName == "DESIGN") {
            val summary = fields["summary"]?.let(::renderValue)?.trim()
            if (!summary.isNullOrBlank()) return summary
        }

        val lines = fields.mapNotNull { (key, value) ->
            val rendered = renderValue(value).trim()
            if (rendered.isBlank()) null else "- **$key**: $rendered"
        }
        if (lines.isEmpty()) return null

        val title = stepName.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        return "# $title\n\n${lines.joinToString("\n")}"
    }

    fun renderValue(value: JsonElement): String = when (value) {
        JsonNull -> ""
        is JsonPrimitive -> value.contentOrNull ?: value.toString()
        is JsonArray -> value.joinToString(", ") { renderValue(it) }
        is JsonObject -> value.entries.joinToString("; ") { (key, child) -> "$key: ${renderValue(child)}" }
    }

    fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (key, child) -> key.toString() to toJsonElement(child) }
        )
        is Iterable<*> -> JsonArray(value.map(::toJsonElement))
        is Array<*> -> JsonArray(value.map(::toJsonElement))
        else -> JsonPrimitive(value.toString())
    }

    fun jsonPrimitiveValue(value: JsonElement): Any? {
        val primitive = value as? JsonPrimitive ?: return renderValue(value)
        return primitive.booleanOrNull
            ?: primitive.longOrNull
            ?: primitive.doubleOrNull
            ?: primitive.contentOrNull
    }
}
