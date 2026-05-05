package com.agentwork.productspecagent.storage

import com.agentwork.productspecagent.domain.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UserStorageTest : S3TestSupport() {

    private fun storage() = UserStorage(objectStore())

    private fun sample(id: String = "u1", email: String = "alice@example.com") = User(
        id = id, email = email, passwordHash = "{bcrypt}xxx",
        createdAt = "2026-05-05T10:00:00Z"
    )

    @Test
    fun `save and load user round-trip`() {
        val s = storage()
        s.saveUser(sample())
        val loaded = s.loadUser("u1")!!
        assertEquals("u1", loaded.id)
        assertEquals("alice@example.com", loaded.email)
    }

    @Test
    fun `loadUser returns null when not found`() {
        assertNull(storage().loadUser("missing"))
    }

    @Test
    fun `findByEmail returns user when registered`() {
        val s = storage()
        s.saveUser(sample(email = "Bob@Example.com"))  // mixed case
        val found = s.findByEmail("bob@example.com")!! // lookup lowercase
        assertEquals("u1", found.id)
    }

    @Test
    fun `findByEmail is case-insensitive on save`() {
        val s = storage()
        s.saveUser(sample(email = "MIXED@case.com"))
        assertNotNull(s.findByEmail("mixed@case.com"))
        assertNotNull(s.findByEmail("MIXED@case.com"))
    }

    @Test
    fun `findByEmail returns null when not registered`() {
        assertNull(storage().findByEmail("nobody@example.com"))
    }

    @Test
    fun `emailExists returns true after save, false otherwise`() {
        val s = storage()
        assertFalse(s.emailExists("alice@example.com"))
        s.saveUser(sample())
        assertTrue(s.emailExists("alice@example.com"))
        assertTrue(s.emailExists("ALICE@example.com"))
    }
}
