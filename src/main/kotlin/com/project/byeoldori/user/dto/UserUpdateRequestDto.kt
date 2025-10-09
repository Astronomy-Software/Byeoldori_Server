package com.project.byeoldori.user.dto

import java.time.LocalDate

data class UserUpdateRequestDto(
    val nickname: String? = null,
    val birthdate: LocalDate? = null,
    val phone: String? = null
)