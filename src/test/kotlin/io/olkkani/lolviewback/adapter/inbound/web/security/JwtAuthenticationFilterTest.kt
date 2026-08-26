package io.olkkani.lolviewback.adapter.inbound.web.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.adapter.inbound.security.JwtAuthenticationFilter
import io.olkkani.lolviewback.application.auth.JwtService
import jakarta.servlet.DispatcherType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {

    private val jwtService = mockk<JwtService>()
    private val filter = JwtAuthenticationFilter(jwtService)

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `a valid bearer token sets the authenticated user id in the security context`() {
        every { jwtService.parseUserId("valid-token") } returns 42L
        val request = mockk<HttpServletRequest>(relaxed = true)
        every { request.getHeader("Authorization") } returns "Bearer valid-token"
        every { request.dispatcherType } returns DispatcherType.REQUEST
        every { request.getAttribute(any()) } returns null
        val response = mockk<HttpServletResponse>()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        assertEquals("42", SecurityContextHolder.getContext().authentication?.principal)
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `a missing Authorization header leaves the security context empty and continues the chain`() {
        val request = mockk<HttpServletRequest>(relaxed = true)
        every { request.getHeader("Authorization") } returns null
        every { request.dispatcherType } returns DispatcherType.REQUEST
        every { request.getAttribute(any()) } returns null
        val response = mockk<HttpServletResponse>()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify { chain.doFilter(request, response) }
    }

    @Test
    fun `an invalid or expired token leaves the security context empty and continues the chain`() {
        every { jwtService.parseUserId("bad-token") } returns null
        val request = mockk<HttpServletRequest>(relaxed = true)
        every { request.getHeader("Authorization") } returns "Bearer bad-token"
        every { request.dispatcherType } returns DispatcherType.REQUEST
        every { request.getAttribute(any()) } returns null
        val response = mockk<HttpServletResponse>()
        val chain = mockk<FilterChain>(relaxed = true)

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify { chain.doFilter(request, response) }
    }
}
