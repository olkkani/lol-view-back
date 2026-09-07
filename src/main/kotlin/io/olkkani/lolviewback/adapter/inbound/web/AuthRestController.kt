package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.inbound.security.CookieSupport
import io.olkkani.lolviewback.adapter.inbound.web.dto.UserIdentityResponse
import io.olkkani.lolviewback.adapter.outbound.persistence.UserIdentityRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.UserRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Role
import io.olkkani.lolviewback.application.auth.JwtService
import io.olkkani.lolviewback.application.auth.RefreshTokenService
import io.olkkani.lolviewback.application.auth.RotateResult
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthRestController(
    private val userIdentityRepository: UserIdentityRepository,
    private val userRepository: UserRepository,
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
    fun refresh(request: HttpServletRequest): ResponseEntity<Void> {
        val refreshCookie =
            request.cookies?.firstOrNull { it.name == "refresh_token" }
                ?: return unauthorized()

        return when (val result = refreshTokenService.rotate(refreshCookie.value)) {
            is RotateResult.Rotated -> newCookiesResponse(result.userId, result.newRawToken)
            is RotateResult.GracePeriodReuse -> newCookiesResponse(result.userId, result.reissuedRawToken)
            RotateResult.TheftDetected, RotateResult.NotFound -> unauthorized(withExpiredCookies = true)
        }
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Void> {
        val refreshCookie = request.cookies?.firstOrNull { it.name == "refresh_token" }
        if (refreshCookie != null) {
            refreshTokenService.revoke(refreshCookie.value)
        }
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, CookieSupport.expiredAccessTokenCookie().toString())
            .header(HttpHeaders.SET_COOKIE, CookieSupport.expiredRefreshTokenCookie().toString())
            .build()
    }

    private fun newCookiesResponse(
        userId: Long,
        newRawRefreshToken: String,
    ): ResponseEntity<Void> {
        // Role is re-read from the DB on every refresh (not carried from the old token) so a
        // promotion or demotion takes effect on the next refresh cycle, not just the next login.
        val role = userRepository.findById(userId).map { it.role }.orElse(Role.USER)
        val accessToken = jwtService.issueToken(userId, role)
        val accessCookie: ResponseCookie = CookieSupport.buildAccessTokenCookie(accessToken, accessExpirationMinutes * 60)
        val refreshCookie: ResponseCookie =
            CookieSupport.buildRefreshTokenCookie(newRawRefreshToken, refreshExpirationDays * 86_400)
        return ResponseEntity
            .noContent()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
            .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            .build()
    }

    private fun unauthorized(withExpiredCookies: Boolean = false): ResponseEntity<Void> {
        val builder = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        if (withExpiredCookies) {
            builder
                .header(HttpHeaders.SET_COOKIE, CookieSupport.expiredAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, CookieSupport.expiredRefreshTokenCookie().toString())
        }
        return builder.build()
    }
}
