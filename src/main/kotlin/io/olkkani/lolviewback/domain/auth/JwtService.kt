package io.olkkani.lolviewback.domain.auth

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.Base64

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration-hours}") private val expirationHours: Long,
) {
    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))

    fun issueToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600_000)
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): Long? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            claims.subject.toLong()
        } catch (ex: ExpiredJwtException) {
            null
        } catch (ex: JwtException) {
            null
        } catch (ex: IllegalArgumentException) {
            null
        }
    }
}
