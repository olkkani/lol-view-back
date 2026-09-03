package io.olkkani.lolviewback.adapter.inbound.security

import org.springframework.http.ResponseCookie
import java.time.Duration

object CookieSupport {
    private const val ACCESS_TOKEN_COOKIE_NAME = "access_token"
    private const val REFRESH_TOKEN_COOKIE_NAME = "refresh_token"

    fun buildAccessTokenCookie(value: String, maxAgeSeconds: Long): ResponseCookie =
        cookie(ACCESS_TOKEN_COOKIE_NAME, value, maxAgeSeconds)

    fun buildRefreshTokenCookie(value: String, maxAgeSeconds: Long): ResponseCookie =
        cookie(REFRESH_TOKEN_COOKIE_NAME, value, maxAgeSeconds)

    fun expiredAccessTokenCookie(): ResponseCookie = cookie(ACCESS_TOKEN_COOKIE_NAME, "", 0)

    fun expiredRefreshTokenCookie(): ResponseCookie = cookie(REFRESH_TOKEN_COOKIE_NAME, "", 0)

    private fun cookie(name: String, value: String, maxAgeSeconds: Long): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path("/auth")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
}
