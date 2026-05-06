package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Service

@Service
class AuthCookieService(private val props: AuthProperties) {

    fun setSessionCookie(response: HttpServletResponse, token: String) {
        val builder = ResponseCookie.from(props.cookie.name, token)
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(props.jwt.expirySeconds)
        if (props.cookie.domain.isNotBlank()) builder.domain(props.cookie.domain)
        response.addHeader("Set-Cookie", builder.build().toString())
    }

    fun clearSessionCookie(response: HttpServletResponse) {
        val builder = ResponseCookie.from(props.cookie.name, "")
            .httpOnly(true)
            .secure(props.cookie.secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(0)
        if (props.cookie.domain.isNotBlank()) builder.domain(props.cookie.domain)
        response.addHeader("Set-Cookie", builder.build().toString())
    }
}
