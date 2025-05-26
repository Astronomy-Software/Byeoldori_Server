package com.project.byeoldori.user.utils

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)