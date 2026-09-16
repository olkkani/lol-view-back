package io.olkkani.lolviewback.application.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

class TelegramLoginVerifierTest {
    private val botToken = "123456:test-bot-token"
    private val verifier = TelegramLoginVerifier(botToken)

    private fun sign(fields: Map<String, String>): String {
        val dataCheckString = fields.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" }
        val secretKey = MessageDigest.getInstance("SHA-256").digest(botToken.toByteArray())
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey, "HmacSHA256"))
        return mac.doFinal(dataCheckString.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun validPayload(authDate: Long = Instant.now().epochSecond): TelegramLoginPayload {
        val fields =
            mapOf(
                "id" to "111222333",
                "first_name" to "Ada",
                "username" to "ada_lovelace",
                "auth_date" to authDate.toString(),
            )
        return TelegramLoginPayload(
            id = fields.getValue("id").toLong(),
            firstName = fields.getValue("first_name"),
            lastName = null,
            username = fields.getValue("username"),
            photoUrl = null,
            authDate = authDate,
            hash = sign(fields),
        )
    }

    @Test
    fun `accepts a correctly signed fresh payload`() {
        assertTrue(verifier.isValid(validPayload()))
    }

    @Test
    fun `rejects a payload with a tampered field`() {
        val payload = validPayload().copy(username = "attacker")
        assertFalse(verifier.isValid(payload))
    }

    @Test
    fun `rejects a payload with a tampered hash`() {
        val payload = validPayload().copy(hash = "0".repeat(64))
        assertFalse(verifier.isValid(payload))
    }

    @Test
    fun `rejects a payload older than 24 hours`() {
        val staleAuthDate = Instant.now().epochSecond - (24 * 60 * 60 + 1)
        val payload = validPayload(authDate = staleAuthDate)
        assertFalse(verifier.isValid(payload))
    }

    @Test
    fun `accepts a payload exactly at the freshness boundary`() {
        val boundaryAuthDate = Instant.now().epochSecond - (24 * 60 * 60 - 5)
        val payload = validPayload(authDate = boundaryAuthDate)
        assertTrue(verifier.isValid(payload))
    }
}
