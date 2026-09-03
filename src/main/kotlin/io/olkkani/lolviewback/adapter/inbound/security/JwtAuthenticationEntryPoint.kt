package io.olkkani.lolviewback.adapter.inbound.security

import io.olkkani.lolviewback.application.auth.JwtParseResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class JwtAuthenticationEntryPoint : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        val header = when (request.getAttribute("jwt.parse.result")) {
            JwtParseResult.Expired -> "Bearer error=\"invalid_token\", error_description=\"expired\""
            else -> "Bearer error=\"invalid_token\""
        }
        response.setHeader("WWW-Authenticate", header)
        response.status = HttpStatus.UNAUTHORIZED.value()
    }
}
