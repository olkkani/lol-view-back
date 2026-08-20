package io.olkkani.lolviewback.infastructure.inbound.web.security

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

class OAuth2FailureHandlerTest {

    private val handler = OAuth2FailureHandler()

    @Test
    fun `consent denial returns 400`() {
        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)
        val exception = OAuth2AuthenticationException(OAuth2Error("access_denied"))

        handler.onAuthenticationFailure(request, response, exception)

        verify { response.status = HttpServletResponse.SC_BAD_REQUEST }
    }
}
