package com.project.byeoldori.user.dto

import java.time.LocalDate

data class UserUpdateRequestDto(
    val nickname: String? = null,
    // 수집 중단(저장하지 않음). 기존 앱이 보내도 400 이 나지 않게 받기만 한다
    val birthdate: LocalDate? = null,
    val phone: String? = null
)

data class ProfileImageUploadResponse(
    val profileImageUrl: String
)