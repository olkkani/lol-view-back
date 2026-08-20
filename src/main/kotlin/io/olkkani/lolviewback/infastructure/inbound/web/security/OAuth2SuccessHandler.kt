package io.olkkani.lolviewback.infastructure.inbound.web.security

import io.olkkani.lolviewback.domain.auth.AuthProvider
import io.olkkani.lolviewback.domain.auth.IdentityAlreadyLinkedException
import io.olkkani.lolviewback.domain.auth.JwtService
import io.olkkani.lolviewback.domain.auth.ResolveIdentityService
import io.olkkani.lolviewback.domain.auth.ResolveResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val resolveIdentityService: ResolveIdentityService,
    private val jwtService: JwtService,
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauthToken = authentication as OAuth2AuthenticationToken
        val oidcUser = oauthToken.principal as OidcUser
        val providerUserId = requireNotNull(oidcUser.subject)

        val result = try {
            resolveIdentityService.resolveIdentity(AuthProvider.GOOGLE, providerUserId, currentSessionUserId = null)
        } catch (ex: IdentityAlreadyLinkedException) {
            response.status = HttpServletResponse.SC_CONFLICT
            return
        }

        val userId = when (result) {
            is ResolveResult.NewUser -> result.userId
            is ResolveResult.LoggedIn -> result.userId
            is ResolveResult.Linked -> result.userId
            ResolveResult.AlreadyLinkedElsewhere -> {
                response.status = HttpServletResponse.SC_CONFLICT
                return
            }
        }

        val token = jwtService.issueToken(userId)
        response.contentType = "application/json"
        response.writer.write("""{"token":"$token"}""")
    }
}
