package io.olkkani.lolviewback.adapter.inbound.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookieSupportTest {

    @Test
    fun `access token cookie is httpOnly, secure, path-scoped to auth, and carries the value`() {
        val cookie = CookieSupport.buildAccessTokenCookie("access-value", maxAgeSeconds = 1800)

        assertEquals("access_token", cookie.name)
        assertEquals("access-value", cookie.value)
        assertTrue(cookie.isHttpOnly)
        assertTrue(cookie.isSecure)
        assertEquals("/auth", cookie.path)
        assertEquals("Lax", cookie.sameSite)
        assertEquals(1800, cookie.maxAge.seconds)
    }

    @Test
    fun `refresh token cookie carries the same attributes with its own name`() {
        val cookie = CookieSupport.buildRefreshTokenCookie("refresh-value", maxAgeSeconds = 1209600)

        assertEquals("refresh_token", cookie.name)
        assertEquals("refresh-value", cookie.value)
        assertTrue(cookie.isHttpOnly)
    }

    @Test
    fun `expired cookies carry a zero max-age and blank value to clear the browser copy`() {
        val cookie = CookieSupport.expiredRefreshTokenCookie()

        assertEquals("refresh_token", cookie.name)
        assertEquals("", cookie.value)
        assertEquals(0, cookie.maxAge.seconds)
    }
}
