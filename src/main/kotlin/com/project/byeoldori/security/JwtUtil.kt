package com.project.byeoldori.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

@Component
class JwtUtil(
    @Value("\${jwt.secret}") private val secretKey: String,
    @Value("\${jwt.access.expiration}") private val accessExpirationMs: Long,
    @Value("\${jwt.refresh.expiration}") private val refreshExpirationMs: Long
) {
    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun generateAccessToken(email: String): String {
        val now = Date()
        val expiry = Date(now.time + accessExpirationMs)
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    fun generateRefreshToken(email: String): String {
        val now = Date()
        val expiry = Date(now.time + refreshExpirationMs)
        return Jwts.builder()
            .setSubject(email)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    // 토큰에서 이메일 추출
    fun extractEmail(token: String): String {
        return getClaims(token).subject
    }

    // 토큰 유효성 검사
    fun validateToken(token: String): Boolean {
        return try {
            val claims = getClaims(token)
            !claims.expiration.before(Date())
        } catch (e: Exception) {
            logger.warn("JWT 유효성 검사 실패: ${e.message}")
            false
        }
    }

    private fun getClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }

    fun extractExpiration(token: String): LocalDateTime {
        val claims = getClaims(token)
        return claims.expiration.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
    }

    fun getSecretKey(): java.security.Key {
        return key
    }
}