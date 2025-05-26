package com.project.byeoldori.user.dto

import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UserUpdateRequestDto(
    @field:Size(min = 2, max = 12, message = "닉네임은 2자 이상 12자 이하여야 합니다.")
    val nickname: String? = null,
    val birthdate: LocalDate? = null
)