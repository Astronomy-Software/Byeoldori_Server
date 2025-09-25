package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetDto(
    @field:NotBlank @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    val password: String,
    @field:NotBlank @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    val confirmPassword: String
)