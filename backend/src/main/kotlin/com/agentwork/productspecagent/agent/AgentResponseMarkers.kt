package com.agentwork.productspecagent.agent

object AgentResponseMarkers {
    fun extractDecisionTitle(raw: String): String? {
        val pattern = Regex("""\[DECISION_NEEDED]\s*[:：]\s*(.+)""")
        val markdownPattern = Regex("""\*?\*?\[DECISION_NEEDED]\*?\*?\s*[:：]\s*(.+)""")
        val match = pattern.find(raw) ?: markdownPattern.find(raw)
        return match?.groupValues?.get(1)?.trim()?.removeSurrounding("**")?.removeSurrounding("`")
    }

    fun extractClarification(raw: String): Pair<String, String>? {
        val pattern = Regex("""\[CLARIFICATION_NEEDED]\s*[:：]\s*([^|]+)\|\s*(.+)""")
        val markdownPattern = Regex("""\*?\*?\[CLARIFICATION_NEEDED]\*?\*?\s*[:：]\s*([^|]+)\|\s*(.+)""")
        val match = pattern.find(raw) ?: markdownPattern.find(raw)
        if (match != null) {
            val question = match.groupValues[1].trim().removeSurrounding("**").removeSurrounding("`")
            val reason = match.groupValues[2].trim().removeSurrounding("**").removeSurrounding("`")
            return Pair(question, reason)
        }

        val fallbackPattern = Regex("""\[CLARIFICATION_NEEDED]\s*[:：]\s*(.+)""")
        val fallbackMatch = fallbackPattern.find(raw)
        if (fallbackMatch != null) {
            val text = fallbackMatch.groupValues[1].trim()
            return Pair(text, "Klärung erforderlich um fortfahren zu können")
        }
        return null
    }

    fun clean(raw: String): String {
        return raw
            .replace("[STEP_COMPLETE]", "")
            .replace(Regex("""\*?\*?\[STEP_SUMMARY]\*?\*?\s*[:：][^\n]*"""), "")
            .replace(Regex("""\*?\*?\[DECISION_NEEDED]\*?\*?\s*[:：][^\n]*"""), "")
            .replace(Regex("""\*?\*?\[CLARIFICATION_NEEDED]\*?\*?\s*[:：][^\n]*"""), "")
            .trim()
    }
}
