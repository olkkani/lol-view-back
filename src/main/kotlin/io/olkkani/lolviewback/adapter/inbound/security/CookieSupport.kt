package io.olkkani.lolviewback.adapter.inbound.security

import org.springframework.http.ResponseCookie
import java.time.Duration

object CookieSupport {
    private const val ACCESS_TOKEN_COOKIE_NAME = "access_token"
    private const val REFRESH_TOKEN_COOKIE_NAME = "refresh_token"

    fun buildAccessTokenCookie(value: String, maxAgeSeconds: Long): ResponseCookie =
        cookie(ACCESS_TOKEN_COOKIE_NAME, value, maxAgeSeconds, path = "/")

    fun buildRefreshTokenCookie(value: String, maxAgeSeconds: Long): ResponseCookie =
        cookie(REFRESH_TOKEN_COOKIE_NAME, value, maxAgeSeconds, path = "/auth")

    fun expiredAccessTokenCookie(): ResponseCookie = cookie(ACCESS_TOKEN_COOKIE_NAME, "", 0, path = "/")

    fun expiredRefreshTokenCookie(): ResponseCookie = cookie(REFRESH_TOKEN_COOKIE_NAME, "", 0, path = "/auth")

    private fun cookie(name: String, value: String, maxAgeSeconds: Long, path: String): ResponseCookie =
        ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .sameSite("Lax")
            .path(path)
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
}
