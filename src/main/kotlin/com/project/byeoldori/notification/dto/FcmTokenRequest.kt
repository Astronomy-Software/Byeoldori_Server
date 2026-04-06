package com.project.byeoldori.notification.dto

import jakarta.validation.constraints.NotBlank

data class FcmTokenRequest(
    @field:NotBlank(message = "FCM 토큰은 필수입니다.")
    val token: String,
    val deviceType: String = "android"
)
