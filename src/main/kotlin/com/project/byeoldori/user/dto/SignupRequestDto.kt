package com.project.byeoldori.user.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.*

data class SignupRequestDto(
    @field:Email(message = "올바른 이메일 형식이어야 합니다.")
    @field:NotBlank(message = "이메일은 필수 입력 항목입니다.")
    val email: String,

    @field:Size(min = 6, message = "비밀번호는 최소 6자 이상이어야 합니다.")
    @field:NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    val password: String,

    @field:Size(min = 6)
    @field:NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    val passwordConfirm: String,

    @field:Valid
    val consents: ConsentDto
) {
    data class ConsentDto(
        @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
        val termsOfService: Boolean,
        @AssertTrue(message = "개인정보 수집 및 이용 동의는 필수입니다.")
        val privacyPolicy: Boolean,
        val marketing: Boolean = false,
        val location: Boolean = false
    )
}