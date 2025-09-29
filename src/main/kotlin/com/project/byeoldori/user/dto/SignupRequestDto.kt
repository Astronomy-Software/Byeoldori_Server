package com.project.byeoldori.user.dto

import jakarta.validation.constraints.*
import java.time.LocalDate

data class SignupRequestDto(
    @field:Email(message = "올바른 이메일 형식이어야 합니다.")
    @field:NotBlank(message = "이메일은 필수 입력 항목입니다.")
    val email: String,

    @field:Size(min = 8, message = "비밀번호는 최소 8자 이상, 영문자, 숫자, 특수문자를 각각 1개 이상 포함이어야 합니다.")
    @field:NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    val password: String,

    @field:Size(min = 8)
    @field:NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    val passwordConfirm: String,

    @field:NotBlank(message = "이름은 필수 입력 항목입니다.")
    val name: String,

    @field:NotBlank(message = "전화번호는 필수 입력 항목입니다.")
    val phone: String,

    val nickname: String? = null,

    val birthdate: LocalDate? = null
)