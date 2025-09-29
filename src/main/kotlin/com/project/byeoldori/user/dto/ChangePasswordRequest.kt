package com.project.byeoldori.user.dto

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ChangePasswordRequest(
    @field:NotBlank
    @com.fasterxml.jackson.annotation.JsonAlias("temporaryPassword")
    val currentPassword: String,

    // 새 비밀번호 정책: 8~64자, 대소문자/숫자/특수문자 각 1개 이상 포함
    @field:NotBlank
    @field:Size(min = 8, max = 64)
    @field:Pattern(
        regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#\$%^&*()-_=+]).{8,64}\$",
        message = "비밀번호는 영문자, 숫자, 특수문자를 각각 1개 이상 포함해야 합니다."
    )
    val newPassword: String,


    // 새 비밀번호 재입력(확인)
    @field:NotBlank
    val confirmNewPassword: String
) {
    @get:AssertTrue(message = "새 비밀번호와 재입력 비밀번호가 일치하지 않습니다.")
    val isConfirmMatched: Boolean
        get() = newPassword == confirmNewPassword
}