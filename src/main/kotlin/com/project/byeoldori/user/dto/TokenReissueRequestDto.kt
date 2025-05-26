package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank

// 토큰 재발급 요청 DTO
data class TokenReissueRequestDto(
    @field:NotBlank(message = "Refresh Token은 필수입니다.")
    val refreshToken: String
)