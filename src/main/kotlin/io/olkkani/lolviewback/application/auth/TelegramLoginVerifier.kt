package io.olkkani.lolviewback.application.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class TelegramLoginVerifier(
    @Value("\${telegram.bot-token}") private val botToken: String,
) {
    fun isValid(payload: TelegramLoginPayload): Boolean {
        if (isStale(payload.authDate)) return false

        val dataCheckString = buildDataCheckString(payload)
        val secretKey = MessageDigest.getInstance("SHA-256").digest(botToken.toByteArray())
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secretKey, HMAC_ALGORITHM))
        val expectedHash = mac.doFinal(dataCheckString.toByteArray()).toHexString()

        return MessageDigest.isEqual(expectedHash.toByteArray(), payload.hash.lowercase().toByteArray())
    }

    private fun isStale(authDate: Long): Boolean = Instant.now().epochSecond - authDate > MAX_AUTH_AGE_SECONDS

    private fun buildDataCheckString(payload: TelegramLoginPayload): String =
        buildMap {
            put("auth_date", payload.authDate.toString())
            put("first_name", payload.firstName)
            put("id", payload.id.toString())
            payload.lastName?.let { put("last_name", it) }
            payload.photoUrl?.let { put("photo_url", it) }
            payload.username?.let { put("username", it) }
        }.toSortedMap()
            .entries
            .joinToString("\n") { "${it.key}=${it.value}" }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val MAX_AUTH_AGE_SECONDS = 24 * 60 * 60
    }
}
