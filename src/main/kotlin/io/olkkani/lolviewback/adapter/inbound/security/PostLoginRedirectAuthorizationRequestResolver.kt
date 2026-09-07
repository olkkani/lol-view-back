package io.olkkani.lolviewback.adapter.inbound.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Encodes the `redirect` query param a frontend passes when it kicks off
 * /oauth2/authorization/google (e.g. after intercepting a 401 on a deep link)
 * into the OAuth2 `state` value, so it survives the round trip to Google and
 * back. `state` is opaque to Google — it returns whatever we sent unchanged —
 * so this rides along with zero extra storage (no session/cookie needed).
 */
@Component
class PostLoginRedirectAuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository,
) : OAuth2AuthorizationRequestResolver {
    private val delegate =
        DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository,
            AUTHORIZATION_REQUEST_BASE_URI,
        )

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? =
        delegate.resolve(request)?.let { encodeRedirectInto(it, request) }

    override fun resolve(
        request: HttpServletRequest,
        clientRegistrationId: String,
    ): OAuth2AuthorizationRequest? = delegate.resolve(request, clientRegistrationId)?.let { encodeRedirectInto(it, request) }

    private fun encodeRedirectInto(
        authorizationRequest: OAuth2AuthorizationRequest,
        request: HttpServletRequest,
    ): OAuth2AuthorizationRequest {
        val redirectPath = request.getParameter(REDIRECT_PARAM) ?: return authorizationRequest
        val originalState = authorizationRequest.state.orEmpty()
        val encodedRedirect =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(redirectPath.toByteArray(StandardCharsets.UTF_8))
        return OAuth2AuthorizationRequest
            .from(authorizationRequest)
            .state("$originalState$STATE_DELIMITER$encodedRedirect")
            .build()
    }

    companion object {
        private const val AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization"
        const val REDIRECT_PARAM = "redirect"
        const val STATE_DELIMITER = "::r::"

        fun extractRedirectPath(state: String?): String? {
            val index = state?.indexOf(STATE_DELIMITER) ?: return null
            if (index < 0) return null
            val encoded = state.substring(index + STATE_DELIMITER.length)
            return runCatching {
                String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8)
            }.getOrNull()
        }
    }
}
