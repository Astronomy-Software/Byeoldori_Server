package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank
    @com.fasterxml.jackson.annotation.JsonAlias("temporaryPassword")
    val currentPassword: String,

    // 새 비밀번호 정책: 8~64자, 대소문자/숫자/특수문자 각 1개 이상 포함
    @field:NotBlank
    @field:Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,64}$",
        message = "비밀번호는 8자 이상이고, 영문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다.")
    val newPassword: String,

    // 새 비밀번호 재입력(확인)
    @field:NotBlank
    @field:Size(min = 8, max = 64)
    val confirmNewPassword: String
)