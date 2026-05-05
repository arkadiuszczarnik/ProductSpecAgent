package com.agentwork.productspecagent.service

import com.agentwork.productspecagent.storage.S3TestSupport
import com.agentwork.productspecagent.storage.UserStorage
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest : S3TestSupport() {

    private val fixedClock = Clock.fixed(Instant.parse("2026-05-05T10:00:00Z"), ZoneOffset.UTC)
    private fun service() = UserService(UserStorage(objectStore()), BCryptPasswordEncoder(10), fixedClock)

    @Test
    fun `register creates user with hashed password`() {
        val u = service().register("alice@example.com", "password123")
        assertEquals("alice@example.com", u.email)
        assertTrue(u.passwordHash.startsWith("\$2a\$") || u.passwordHash.startsWith("\$2b\$"))
        assertNotEquals("password123", u.passwordHash)
    }

    @Test
    fun `register lowercases email`() {
        val u = service().register("Alice@EXAMPLE.com", "password123")
        assertEquals("alice@example.com", u.email)
    }

    @Test
    fun `register throws for weak password (less than 8 chars)`() {
        assertThrows(WeakPasswordException::class.java) {
            service().register("a@b.com", "short")
        }
    }

    @Test
    fun `register throws for password longer than 128 chars`() {
        assertThrows(WeakPasswordException::class.java) {
            service().register("a@b.com", "a".repeat(129))
        }
    }

    @Test
    fun `register throws for invalid email format`() {
        assertThrows(InvalidEmailException::class.java) { service().register("not-an-email", "password123") }
        assertThrows(InvalidEmailException::class.java) { service().register("@no-local.com", "password123") }
        assertThrows(InvalidEmailException::class.java) { service().register("no-at.com", "password123") }
    }

    @Test
    fun `register throws when email already registered (case-insensitive)`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertThrows(EmailAlreadyExistsException::class.java) {
            s.register("Alice@EXAMPLE.com", "password456")
        }
    }

    @Test
    fun `authenticate returns user for correct credentials`() {
        val s = service()
        val registered = s.register("alice@example.com", "password123")
        val authed = s.authenticate("alice@example.com", "password123")
        assertEquals(registered.id, authed.id)
    }

    @Test
    fun `authenticate is case-insensitive on email`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertNotNull(s.authenticate("Alice@EXAMPLE.com", "password123"))
    }

    @Test
    fun `authenticate throws for wrong password`() {
        val s = service()
        s.register("alice@example.com", "password123")
        assertThrows(InvalidCredentialsException::class.java) {
            s.authenticate("alice@example.com", "wrong")
        }
    }

    @Test
    fun `authenticate throws for unknown email`() {
        assertThrows(InvalidCredentialsException::class.java) {
            service().authenticate("nobody@example.com", "password123")
        }
    }
}
