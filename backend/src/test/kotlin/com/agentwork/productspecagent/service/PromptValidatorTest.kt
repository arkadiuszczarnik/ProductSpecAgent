package com.agentwork.productspecagent.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class PromptValidatorTest {

    @Test
    fun `NotBlank passes on non-blank content`() {
        assertTrue(PromptValidator.NotBlank.validate("hello").isEmpty())
    }

    @Test
    fun `NotBlank fails on empty content`() {
        val errors = PromptValidator.NotBlank.validate("")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("nicht leer"))
    }

    @Test
    fun `NotBlank fails on whitespace-only content`() {
        assertEquals(1, PromptValidator.NotBlank.validate("   \n\t  ").size)
    }

    @Test
    fun `MaxLength passes when under limit`() {
        assertTrue(PromptValidator.MaxLength(100).validate("short").isEmpty())
    }

    @Test
    fun `MaxLength fails when over limit and reports actual length`() {
        val errors = PromptValidator.MaxLength(5).validate("hello!")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("Maximal 5"))
        assertTrue(errors[0].contains("aktuell 6"))
    }

    @Test
    fun `RequiresAll passes when all tokens are present`() {
        val v = PromptValidator.RequiresAll(listOf("[A]", "[B]"), "reason")
        assertTrue(v.validate("text [A] more [B] end").isEmpty())
    }

    @Test
    fun `RequiresAll fails listing missing tokens with reason`() {
        val v = PromptValidator.RequiresAll(listOf("[A]", "[B]", "[C]"), "weil das wichtig ist")
        val errors = v.validate("only [A] here")
        assertEquals(1, errors.size)
        assertTrue(errors[0].contains("[B]"))
        assertTrue(errors[0].contains("[C]"))
        assertTrue(errors[0].contains("weil das wichtig ist"))
    }
}
