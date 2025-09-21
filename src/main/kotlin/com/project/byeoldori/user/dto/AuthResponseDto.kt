package com.project.byeoldori.user.dto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    // AccessToken 만료시각 (epoch millis)
    val accessTokenExpiresAt: String,
    // RefreshToken 만료시각 (epoch millis)
    val refreshTokenExpiresAt: String? = null
) {
    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        fun of(access: String, refresh: String, accessExp: Instant, refreshExp: Instant?) =
            AuthResponseDto(
                accessToken = access,
                refreshToken = refresh,
                accessTokenExpiresAt = accessExp.atZone(ZONE).format(ISO),
                refreshTokenExpiresAt = refreshExp?.atZone(ZONE)?.format(ISO)
            )
    }
}