package com.project.byeoldori.user.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequestDto(
    @field:Email(message = "유효한 이메일 형식이어야 합니다.")
    @field:NotBlank(message = "이메일은 필수 입력 항목입니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수 항목입니다.")
    val password: String
)