package com.agentwork.productspecagent.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
    val jwt: Jwt = Jwt(),
    val cookie: Cookie = Cookie(),
) {
    data class Jwt(
        val secret: String = "",
        val expirySeconds: Long = 604800, // 7 days
    )
    data class Cookie(
        val name: String = "session",
        val secure: Boolean = true,
    )
}
