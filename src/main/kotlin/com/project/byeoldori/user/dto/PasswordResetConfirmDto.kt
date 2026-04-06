package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank

data class PasswordResetConfirmDto(
    @field:NotBlank(message = "토큰은 필수 입력 항목입니다.")
    val token: String,

    @field:NotBlank(message = "새 비밀번호는 필수 입력 항목입니다.")
    val newPassword: String,

    @field:NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    val confirmNewPassword: String
)
