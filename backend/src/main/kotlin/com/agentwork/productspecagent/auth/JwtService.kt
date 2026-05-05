package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import javax.crypto.SecretKey

data class JwtPayload(val userId: String, val email: String)

@Service
@Primary
class JwtService(
    private val props: AuthProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val key: SecretKey = run {
        check(props.jwt.secret.isNotBlank()) { "auth.jwt.secret must be set (e.g., AUTH_JWT_SECRET env var)" }
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray(Charsets.UTF_8))
    }

    fun sign(userId: String, email: String): String {
        val now = clock.instant()
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.jwt.expirySeconds)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): JwtPayload? = try {
        val claims = Jwts.parser()
            .verifyWith(key)
            .clock { Date.from(clock.instant()) }
            .build()
            .parseSignedClaims(token)
            .payload
        JwtPayload(
            userId = claims.subject,
            email = claims["email", String::class.java],
        )
    } catch (_: Exception) {
        null
    }
}
