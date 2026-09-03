package io.olkkani.lolviewback.adapter.inbound.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.application.auth.JwtParseResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException

class JwtAuthenticationEntryPointTest {

    private val entryPoint = JwtAuthenticationEntryPoint()

    @Test
    fun `an expired token sets the expired error code on the WWW-Authenticate header`() {
        val request = mockk<HttpServletRequest>()
        every { request.getAttribute("jwt.parse.result") } returns JwtParseResult.Expired
        val response = mockk<HttpServletResponse>(relaxed = true)

        entryPoint.commence(request, response, mockk<AuthenticationException>())

        verify { response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\", error_description=\"expired\"") }
        verify { response.status = HttpStatus.UNAUTHORIZED.value() }
    }

    @Test
    fun `a missing or invalid token sets no expired marker on the WWW-Authenticate header`() {
        val request = mockk<HttpServletRequest>()
        every { request.getAttribute("jwt.parse.result") } returns null
        val response = mockk<HttpServletResponse>(relaxed = true)

        entryPoint.commence(request, response, mockk<AuthenticationException>())

        verify { response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"") }
        verify { response.status = HttpStatus.UNAUTHORIZED.value() }
    }
}
