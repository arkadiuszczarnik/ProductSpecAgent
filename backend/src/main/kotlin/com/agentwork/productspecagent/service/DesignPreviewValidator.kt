package com.agentwork.productspecagent.service

import org.springframework.stereotype.Component

class InvalidDesignPreviewException(reason: String) : RuntimeException("Invalid design preview: $reason")

@Component
class DesignPreviewValidator {
    private val forbiddenPatterns = listOf(
        Regex("""(?i)\bhttps?://""") to "external URLs are not allowed",
        Regex("""(?i)\b(src|href|srcset)\s*=\s*["']?\s*//""") to "protocol-relative URLs are not allowed",
        Regex("""(?i)\burl\s*\(\s*["']?\s*//""") to "protocol-relative CSS URLs are not allowed",
        Regex("""(?i)@import\s+(?:url\s*\(\s*)?["']?\s*(?:https?://|//)""") to "external CSS imports are not allowed",
        Regex("""(?i)<script[^>]+\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""") to "external scripts are not allowed",
        Regex("""(?i)\bfetch\s*\(""") to "fetch is not allowed",
        Regex("""(?i)\bwindow\s*\[\s*["']fetch["']\s*]\s*\(""") to "fetch is not allowed",
        Regex("""(?i)\bXMLHttpRequest\b""") to "XMLHttpRequest is not allowed",
        Regex("""(?i)\bWebSocket\b""") to "WebSocket is not allowed",
        Regex("""(?i)\bEventSource\b""") to "EventSource is not allowed",
        Regex("""(?i)\bnavigator\.sendBeacon\s*\(""") to "sendBeacon is not allowed",
        Regex("""(?i)\bimport\s*\(""") to "dynamic import is not allowed",
        Regex("""(?i)\blocalStorage\b""") to "localStorage is not allowed",
        Regex("""(?i)\bsessionStorage\b""") to "sessionStorage is not allowed",
        Regex("""(?i)\bdocument\.cookie\b""") to "cookie access is not allowed",
        Regex("""(?i)\b(?:window\.)?parent\s*\.""") to "parent window access is not allowed",
        Regex("""(?i)\bpostMessage\s*\(""") to "postMessage is not allowed",
        Regex("""(?i)<form[^>]+\baction\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""") to "form actions are not allowed",
    )

    fun validate(html: String) {
        if (html.isBlank()) {
            throw InvalidDesignPreviewException("html is blank")
        }
        if (html.toByteArray(Charsets.UTF_8).size > 500_000) {
            throw InvalidDesignPreviewException("html exceeds 500 KB")
        }
        val normalizedHtml = decodeHtmlEntities(html)
        val hit = forbiddenPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(normalizedHtml) }
        if (hit != null) {
            throw InvalidDesignPreviewException(hit.second)
        }
    }

    private fun decodeHtmlEntities(html: String): String {
        return htmlEntityPattern.replace(html) { match ->
            when (val entity = match.groupValues[1].lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "colon" -> ":"
                else -> decodeNumericEntity(entity) ?: match.value
            }
        }
    }

    private fun decodeNumericEntity(entity: String): String? {
        val codePoint = when {
            entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> null
        } ?: return null
        return runCatching { String(Character.toChars(codePoint)) }.getOrNull()
    }

    private companion object {
        private val htmlEntityPattern = Regex(
            """&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|colon);""",
            RegexOption.IGNORE_CASE,
        )
    }
}
