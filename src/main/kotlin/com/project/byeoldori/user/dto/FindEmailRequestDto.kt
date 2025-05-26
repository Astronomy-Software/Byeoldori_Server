package com.project.byeoldori.user.dto

import jakarta.validation.constraints.NotBlank

data class FindEmailRequestDto(
    @field:NotBlank(message = "이름은 필수 입력 항목입니다.")
    val name: String,
    @field:NotBlank(message = "전화번호는 필수 입력 항목입니다.")
    val phone: String
)