package com.agentwork.productspecagent.service

import org.springframework.stereotype.Component

class InvalidDesignPreviewException(reason: String) : RuntimeException("Invalid design preview: $reason")

@Component
class DesignPreviewValidator {
    private val forbiddenPatterns = listOf(
        Regex("""(?i)\bhttps?://""") to "external URLs are not allowed",
        Regex("""(?i)\b(src|href)\s*=\s*["']\s*//""") to "protocol-relative URLs are not allowed",
        Regex("""(?i)<script[^>]+\bsrc\s*=""") to "external scripts are not allowed",
        Regex("""(?i)\bfetch\s*\(""") to "fetch is not allowed",
        Regex("""(?i)\bXMLHttpRequest\b""") to "XMLHttpRequest is not allowed",
        Regex("""(?i)\bWebSocket\b""") to "WebSocket is not allowed",
        Regex("""(?i)\bEventSource\b""") to "EventSource is not allowed",
        Regex("""(?i)\blocalStorage\b""") to "localStorage is not allowed",
        Regex("""(?i)\bsessionStorage\b""") to "sessionStorage is not allowed",
        Regex("""(?i)\bdocument\.cookie\b""") to "cookie access is not allowed",
        Regex("""(?i)\bwindow\.parent\b""") to "parent window access is not allowed",
        Regex("""(?i)\bpostMessage\s*\(""") to "postMessage is not allowed",
        Regex("""(?i)<form[^>]+\baction\s*=""") to "form actions are not allowed",
    )

    fun validate(html: String) {
        if (html.isBlank()) {
            throw InvalidDesignPreviewException("html is blank")
        }
        if (html.length > 500_000) {
            throw InvalidDesignPreviewException("html exceeds 500 KB")
        }
        val hit = forbiddenPatterns.firstOrNull { (pattern, _) -> pattern.containsMatchIn(html) }
        if (hit != null) {
            throw InvalidDesignPreviewException(hit.second)
        }
    }
}
