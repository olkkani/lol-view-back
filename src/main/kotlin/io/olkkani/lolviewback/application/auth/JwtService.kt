package io.olkkani.lolviewback.application.auth

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.Date

sealed class JwtParseResult {
    data class Valid(val userId: Long) : JwtParseResult()
    object Expired : JwtParseResult()
    object Invalid : JwtParseResult()
}

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access-expiration-minutes}") private val accessExpirationMinutes: Long,
) {
    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))

    fun issueToken(userId: Long): String {
        val now = Date()
        val expiry = Date(now.time + accessExpirationMinutes * 60_000)
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun parseUserId(token: String): Long? {
        val result = parseResult(token)
        return (result as? JwtParseResult.Valid)?.userId
    }

    fun parseResult(token: String): JwtParseResult {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
            JwtParseResult.Valid(claims.subject.toLong())
        } catch (ex: ExpiredJwtException) {
            JwtParseResult.Expired
        } catch (ex: JwtException) {
            JwtParseResult.Invalid
        } catch (ex: IllegalArgumentException) {
            JwtParseResult.Invalid
        }
    }
}
