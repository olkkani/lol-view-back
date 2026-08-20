package io.olkkani.lolviewback.domain.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class JwtServiceTest {

    private val secret = Base64.getEncoder().encodeToString("a".repeat(32).toByteArray())
    private val service = JwtService(secret = secret, expirationHours = 24)

    @Test
    fun `a token issued for a user parses back to the same user id`() {
        val token = service.issueToken(userId = 42L)

        val parsed = service.parseUserId(token)

        assertEquals(42L, parsed)
    }

    @Test
    fun `an expired token fails to parse`() {
        val expiredService = JwtService(secret = secret, expirationHours = -1)
        val token = expiredService.issueToken(userId = 42L)

        val parsed = service.parseUserId(token)

        assertNull(parsed)
    }

    @Test
    fun `a token signed with a different secret fails to parse`() {
        val otherSecret = Base64.getEncoder().encodeToString("b".repeat(32).toByteArray())
        val otherService = JwtService(secret = otherSecret, expirationHours = 24)
        val token = otherService.issueToken(userId = 42L)

        val parsed = service.parseUserId(token)

        assertNull(parsed)
    }

    @Test
    fun `a malformed token fails to parse`() {
        val parsed = service.parseUserId("not-a-real-jwt")

        assertNull(parsed)
    }
}
