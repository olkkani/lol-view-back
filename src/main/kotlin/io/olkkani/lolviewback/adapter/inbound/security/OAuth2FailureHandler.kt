package io.olkkani.lolviewback.adapter.inbound.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuth2FailureHandler(
    @Value("\${app.frontend-url}") private val frontendUrl: String,
) : AuthenticationFailureHandler {
    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        // Exception detail is intentionally not exposed to the client - log server-side only
        // if needed. The frontend gets a generic error flag it can show a message for.
        response.sendRedirect("$frontendUrl/login?error=oauth_failed")
    }
}
