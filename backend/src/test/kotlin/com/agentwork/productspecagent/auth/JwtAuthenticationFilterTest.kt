package com.agentwork.productspecagent.auth

import com.agentwork.productspecagent.config.AuthProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {

    private val props = AuthProperties(jwt = AuthProperties.Jwt(secret = "0123456789abcdef0123456789abcdef0123456789abcdef", expirySeconds = 60))
    private val jwt = JwtService(props)
    private val filter = JwtAuthenticationFilter(jwt, props)

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `valid cookie sets SecurityContext authentication`() {
        val token = jwt.sign("u-1", "a@b.com")
        val req = MockHttpServletRequest().apply { setCookies(Cookie("session", token)) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("u-1", auth!!.name)
        assertTrue(auth.isAuthenticated)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `missing cookie leaves SecurityContext empty`() {
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `invalid token cookie leaves SecurityContext empty without throwing`() {
        val req = MockHttpServletRequest().apply { setCookies(Cookie("session", "garbage")) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        assertDoesNotThrow { filter.doFilter(req, res, chain) }
        assertNull(SecurityContextHolder.getContext().authentication)
        verify(chain).doFilter(req, res)
    }

    @Test
    fun `wrong cookie name is ignored`() {
        val token = jwt.sign("u-1", "a@b.com")
        val req = MockHttpServletRequest().apply { setCookies(Cookie("other", token)) }
        val res = MockHttpServletResponse()
        val chain: FilterChain = mock()

        filter.doFilter(req, res, chain)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
