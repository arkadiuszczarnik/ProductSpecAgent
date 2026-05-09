package com.agentwork.productspecagent.service

import org.springframework.stereotype.Component

class InvalidDesignPreviewException(reason: String) : RuntimeException("Invalid design preview: $reason")

@Component
class DesignPreviewValidator {
    private val forbiddenPatterns = listOf(
        Regex("""(?i)\bhttps?://""") to "external URLs are not allowed",
        Regex("""(?i)\b(src|href|srcset)\s*=\s*["']?\s*//""") to "protocol-relative URLs are not allowed",
        Regex("""(?i)\bsrcset\s*=\s*(?:"[^"]*//|'[^']*//|[^>]*//)""") to "protocol-relative URLs are not allowed",
        Regex("""(?i)\burl\s*\(\s*["']?\s*//""") to "protocol-relative CSS URLs are not allowed",
        Regex("""(?i)@import\s+(?:url\s*\(\s*)?["']?\s*(?:https?://|//)""") to "external CSS imports are not allowed",
        Regex("""(?i)<script[^>]+\bsrc\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""") to "external scripts are not allowed",
        Regex("""(?i)(?:\bfetch\b|["']fetch["'])""") to "fetch is not allowed",
        Regex("""(?i)(?:\bXMLHttpRequest\b|["']XMLHttpRequest["'])""") to "XMLHttpRequest is not allowed",
        Regex("""(?i)(?:\bWebSocket\b|["']WebSocket["'])""") to "WebSocket is not allowed",
        Regex("""(?i)(?:\bEventSource\b|["']EventSource["'])""") to "EventSource is not allowed",
        Regex("""(?i)(?:\bsendBeacon\b|["']sendBeacon["'])""") to "sendBeacon is not allowed",
        Regex("""(?i)\bimport\s*\(""") to "dynamic import is not allowed",
        Regex("""(?i)(?:\blocalStorage\b|["']localStorage["'])""") to "localStorage is not allowed",
        Regex("""(?i)(?:\bsessionStorage\b|["']sessionStorage["'])""") to "sessionStorage is not allowed",
        Regex("""(?i)(?:\bcookie\b|["']cookie["'])""") to "cookie access is not allowed",
        Regex("""(?i)(?:\bparent\b|["']parent["'])""") to "parent window access is not allowed",
        Regex("""(?i)(?:\bpostMessage\b|["']postMessage["'])""") to "postMessage is not allowed",
        Regex("""(?i)<form[^>]+\baction\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""") to "form actions are not allowed",
        Regex("""(?i)\bformaction\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""") to "form actions are not allowed",
    )

    fun validate(html: String) {
        if (html.isBlank()) {
            throw InvalidDesignPreviewException("html is blank")
        }
        if (html.toByteArray(Charsets.UTF_8).size > 500_000) {
            throw InvalidDesignPreviewException("html exceeds 500 KB")
        }
        val normalizedHtml = removeAsciiControlCharacters(decodeHtmlEntities(html))
        rejectLocalAttributeUrls(normalizedHtml)
        rejectLocalCssUrls(normalizedHtml)
        val hit = forbiddenPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(normalizedHtml) }
        if (hit != null) {
            throw InvalidDesignPreviewException(hit.second)
        }
    }

    private fun rejectLocalAttributeUrls(html: String) {
        urlAttributePattern.findAll(html).forEach { match ->
            val attribute = match.groupValues[1].lowercase()
            val value = match.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            if (hasLocalNetworkUrl(value)) {
                throw InvalidDesignPreviewException("$attribute URLs must be inline or data images")
            }
        }
        srcsetAttributePattern.findAll(html).forEach { match ->
            val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            val candidates = value.split(",").map { it.trim().substringBefore(' ').trim() }
            if (candidates.any(::hasLocalNetworkUrl)) {
                throw InvalidDesignPreviewException("srcset URLs must be inline or data images")
            }
        }
    }

    private fun rejectLocalCssUrls(html: String) {
        cssUrlPattern.findAll(html).forEach { match ->
            val value = match.groupValues[2].trim()
            if (hasLocalNetworkUrl(value)) {
                throw InvalidDesignPreviewException("CSS URLs must be inline or data images")
            }
        }
        cssImportPattern.findAll(html).forEach { match ->
            val value = match.groupValues[2].trim()
            if (hasLocalNetworkUrl(value)) {
                throw InvalidDesignPreviewException("CSS imports are not allowed")
            }
        }
    }

    private fun hasLocalNetworkUrl(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("#")) return false
        if (trimmed.startsWith("data:image/", ignoreCase = true)) return false
        if (trimmed.startsWith("javascript:", ignoreCase = true)) return false
        if (trimmed.startsWith("mailto:", ignoreCase = true)) return false
        if (trimmed.startsWith("tel:", ignoreCase = true)) return false
        return !absoluteSchemePattern.containsMatchIn(trimmed)
    }

    private fun decodeHtmlEntities(html: String): String {
        val urlNormalizedHtml = html
            .replace(semicolonlessHexColonPattern, ":")
            .replace(semicolonlessDecimalColonPattern, ":")
            .replace(semicolonlessHexSlashPattern, "/")
            .replace(semicolonlessDecimalSlashPattern, "/")

        return htmlEntityPattern.replace(urlNormalizedHtml) { match ->
            when (val entity = match.groupValues[1].removeSuffix(";").lowercase()) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "colon" -> ":"
                "sol" -> "/"
                "newline" -> "\n"
                "tab" -> "\t"
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

    private fun removeAsciiControlCharacters(html: String): String {
        return asciiControlCharacters.replace(html, "")
    }

    private companion object {
        private val asciiControlCharacters = Regex("""[\u0000-\u001F\u007F]""")
        private val htmlEntityPattern = Regex(
            """&(#x[0-9a-fA-F]+;?|#[0-9]+;?|amp;|lt;|gt;|quot;|apos;|colon;|sol;|newline;|tab;)""",
            RegexOption.IGNORE_CASE,
        )
        private val semicolonlessHexColonPattern = Regex("""&#x0*3a;?""", RegexOption.IGNORE_CASE)
        private val semicolonlessDecimalColonPattern = Regex("""&#0*58;?""")
        private val semicolonlessHexSlashPattern = Regex("""&#x0*2f;?""", RegexOption.IGNORE_CASE)
        private val semicolonlessDecimalSlashPattern = Regex("""&#0*47;?""")
        private val absoluteSchemePattern = Regex("""^[a-z][a-z0-9+.-]*:""", RegexOption.IGNORE_CASE)
        private val urlAttributePattern = Regex(
            """(?is)\b(src|href|poster|data|action|formaction)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        )
        private val srcsetAttributePattern = Regex(
            """(?is)\bsrcset\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
        )
        private val cssUrlPattern = Regex(
            """(?is)\burl\s*\(\s*(["']?)(.*?)\1\s*\)""",
        )
        private val cssImportPattern = Regex(
            """(?is)@import\s+(?:url\s*\(\s*)?(["']?)([^"')\s;]+)""",
        )
    }
}
