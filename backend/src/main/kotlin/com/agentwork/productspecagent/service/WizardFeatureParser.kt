package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.domain.FeatureScope

object WizardFeatureParser {
    private val uuid = java.util.UUID::randomUUID

    fun parse(raw: Any?, category: String?): List<WizardFeatureInput> {
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
        for (edge in edgesRaw) {
            val map = edge as? Map<*, *> ?: continue
            val from = map["from"]?.toString() ?: continue
            val to = map["to"]?.toString() ?: continue
            dependsByTarget.getOrPut(to) { mutableListOf() }.add(from)
        }

        val result = mutableListOf<WizardFeatureInput>()
        for (feature in featuresRaw) {
            val map = feature as? Map<*, *> ?: if (feature is String) mapOf("title" to feature) else continue
            val title = (map["title"] ?: map["name"])?.toString()?.trim()
            if (title.isNullOrBlank()) continue
            val id = map["id"]?.toString()?.ifBlank { null } ?: uuid().toString()
            val description = (map["description"] ?: map["desc"])?.toString() ?: ""
            val scopes = parseScopes(map["scopes"], defaultScopes)
            @Suppress("UNCHECKED_CAST")
            val scopeFields = (map["scopeFields"] as? Map<String, String>) ?: emptyMap()
            result.add(
                WizardFeatureInput(
                    id = id,
                    title = title,
                    description = description,
                    scopes = scopes,
                    scopeFields = scopeFields,
                    dependsOn = dependsByTarget[id] ?: emptyList(),
                )
            )
        }
        return result
    }

    private fun parseScopes(raw: Any?, fallback: Set<FeatureScope>): Set<FeatureScope> {
        val list = raw as? List<*> ?: return fallback
        return list.mapNotNull { scope ->
            runCatching { FeatureScope.valueOf(scope.toString().uppercase()) }.getOrNull()
        }.toSet().ifEmpty { fallback }
    }

    private fun defaultScopesFor(category: String?): Set<FeatureScope> = when (category) {
        "SaaS", "Mobile App", "Desktop App" -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
        "API", "CLI Tool" -> setOf(FeatureScope.BACKEND)
        "Library" -> emptySet()
        else -> setOf(FeatureScope.FRONTEND, FeatureScope.BACKEND)
    }
}
