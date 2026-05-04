package com.project.byeoldori.user.dto

data class NaverLoginRequest(
    val code: String,
    val redirectUri: String
)
