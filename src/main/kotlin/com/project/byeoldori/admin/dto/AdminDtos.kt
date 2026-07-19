package com.project.byeoldori.admin.dto

import jakarta.validation.constraints.NotBlank

data class UpdateConfigRequest(
    @field:NotBlank(message = "설정 키는 필수입니다.")
    val key: String,
    val value: String
)
