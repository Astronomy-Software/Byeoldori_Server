package com.project.byeoldori.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.access.expiration:3600000}") private val accessExpMs: Long,
    @Value("\${jwt.refresh.expiration:1209600000}") private val refreshExpMs: Long
) {
    val zoneId: ZoneId = ZoneId.of("Asia/Seoul")

    private val key: SecretKey = Keys.hmacShaKeyFor(
        MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(StandardCharsets.UTF_8))
    )

    private fun parser() = Jwts.parserBuilder().setSigningKey(key).build()

    fun validateToken(token: String): Boolean = try {
        parser().parseClaimsJws(token) // 서명/만료/형식 검증
        true
    } catch (_: JwtException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

    fun generateAccessToken(email: String): String =
        buildToken(email, accessExpMs, "access")

    fun generateRefreshToken(email: String): String =
        buildToken(email, refreshExpMs, "refresh")

    fun extractEmail(token: String): String =
        parser().parseClaimsJws(token).body.subject

    fun extractExpiration(token: String): LocalDateTime {
        val exp = Jwts.parserBuilder().setSigningKey(key).build()
            .parseClaimsJws(token).body.expiration
        return LocalDateTime.ofInstant(exp.toInstant(), zoneId)
    }

    fun isTokenType(token: String, expected: String): Boolean {
        val typ = Jwts.parserBuilder().setSigningKey(key).build()
            .parseClaimsJws(token).body["typ"] as? String
        return typ == expected
    }

    private fun buildToken(email: String, ttlMs: Long, typ: String): String {
        val now = LocalDateTime.now(zoneId)
        val exp = now.plus(Duration.ofMillis(ttlMs))
        val nowDate = Date.from(now.atZone(zoneId).toInstant())
        val expDate = Date.from(exp.atZone(zoneId).toInstant())

        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(nowDate)
            .setExpiration(expDate)
            .claim("typ", typ) // "access" | "refresh"
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
}