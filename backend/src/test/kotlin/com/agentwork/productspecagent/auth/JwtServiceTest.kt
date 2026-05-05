package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class JwtServiceTest {

    private val testSecret = "0123456789abcdef0123456789abcdef0123456789abcdef" // 48 bytes
    private val props = AuthProperties(jwt = AuthProperties.Jwt(secret = testSecret, expirySeconds = 60))

    private fun service(clock: Clock = Clock.systemUTC()) = JwtService(props, clock)

    @Test
    fun `sign and parse round-trip preserves userId and email`() {
        val s = service()
        val token = s.sign("user-123", "alice@example.com")
        val parsed = s.parse(token)!!
        assertEquals("user-123", parsed.userId)
        assertEquals("alice@example.com", parsed.email)
    }

    @Test
    fun `parse returns null for tampered token`() {
        val s = service()
        val token = s.sign("u", "e@x.com")
        val tampered = token.dropLast(2) + "XX"
        assertNull(s.parse(tampered))
    }

    @Test
    fun `parse returns null for token signed with different secret`() {
        val other = JwtService(
            AuthProperties(jwt = AuthProperties.Jwt(secret = "different-secret-different-secret-different", expirySeconds = 60)),
            Clock.systemUTC()
        )
        val foreign = other.sign("u", "e@x.com")
        assertNull(service().parse(foreign))
    }

    @Test
    fun `parse returns null for expired token`() {
        val past = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val now = Clock.fixed(Instant.parse("2020-01-01T00:02:00Z"), ZoneOffset.UTC) // 2 min later, expiry 60s
        val token = JwtService(props, past).sign("u", "e@x.com")
        assertNull(JwtService(props, now).parse(token))
    }

    @Test
    fun `parse returns null for malformed token`() {
        assertNull(service().parse("not-a-jwt"))
        assertNull(service().parse(""))
    }

    @Test
    fun `JwtService throws on construction with empty secret`() {
        val empty = AuthProperties(jwt = AuthProperties.Jwt(secret = "", expirySeconds = 60))
        assertThrows(IllegalStateException::class.java) {
            JwtService(empty, Clock.systemUTC())
        }
    }
}
