package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetConfirmDto(
    @field:NotBlank(message = "토큰은 필수 항목입니다.")
    val token: String,

    @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    @field:NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    val newPassword: String
)