package com.project.byeoldori.user.dto

data class KakaoLoginRequest(
    val code: String,
    val redirectUri: String
)
