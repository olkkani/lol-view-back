package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.security.CookieSupport
import io.olkkani.lolviewback.adapter.inbound.web.dto.UserIdentityResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.RotateResult
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthRestController(
    private val userIdentityRepository: UserIdentityRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    @Value("\${jwt.access-expiration-minutes}") private val accessExpirationMinutes: Long,
    @Value("\${jwt.refresh-expiration-days}") private val refreshExpirationDays: Long,
) {
    @GetMapping("/me")
    fun getMe(): List<UserIdentityResponse> {
        val userId = SecurityContextHolder.getContext().authentication!!.principal as String
        return userIdentityRepository.findByUserId(userId.toLong()).map {
            UserIdentityResponse(provider = it.provider, providerUserId = it.providerUserId)
        }
    }

    @PostMapping("/refresh")
    fun refresh(request: HttpServletRequest, response: HttpServletResponse) {
        val refreshCookie = request.cookies?.firstOrNull { it.name == "refresh_token" }
        if (refreshCookie == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        when (val result = refreshTokenService.rotate(refreshCookie.value)) {
            is RotateResult.Rotated -> issueNewCookies(response, result.userId, result.newRawToken)
            is RotateResult.GracePeriodReuse -> issueNewCookies(response, result.userId, result.reissuedRawToken)
            RotateResult.TheftDetected, RotateResult.NotFound -> {
                response.addHeader("Set-Cookie", CookieSupport.expiredAccessTokenCookie().toString())
                response.addHeader("Set-Cookie", CookieSupport.expiredRefreshTokenCookie().toString())
                response.status = HttpServletResponse.SC_UNAUTHORIZED
            }
        }
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        val refreshCookie = request.cookies?.firstOrNull { it.name == "refresh_token" }
        if (refreshCookie != null) {
            refreshTokenService.revoke(refreshCookie.value)
        }
        response.addHeader("Set-Cookie", CookieSupport.expiredAccessTokenCookie().toString())
        response.addHeader("Set-Cookie", CookieSupport.expiredRefreshTokenCookie().toString())
    }

    private fun issueNewCookies(response: HttpServletResponse, userId: Long, newRawRefreshToken: String) {
        val accessToken = jwtService.issueToken(userId)
        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildAccessTokenCookie(accessToken, accessExpirationMinutes * 60).toString(),
        )
        response.addHeader(
            "Set-Cookie",
            CookieSupport.buildRefreshTokenCookie(newRawRefreshToken, refreshExpirationDays * 86_400).toString(),
        )
    }
}
