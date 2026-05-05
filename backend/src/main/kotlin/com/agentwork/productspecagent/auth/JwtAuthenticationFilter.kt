package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val props: AuthProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = request.cookies?.firstOrNull { it.name == props.cookie.name }?.value
        if (token != null) {
            val payload = jwtService.parse(token)
            if (payload != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    payload.userId,
                    null,
                    emptyList()
                )
                SecurityContextHolder.getContext().authentication = auth
                request.setAttribute("authEmail", payload.email)
            }
        }
        filterChain.doFilter(request, response)
    }
}
