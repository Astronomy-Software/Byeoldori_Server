package com.project.byeoldori.common.web

data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String = "OK",
    val data: T? = null
) {
    companion object {
        fun ok(): ApiResponse<Unit> = ApiResponse()
        fun ok(message: String): ApiResponse<Unit> = ApiResponse(message = message)
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(data = data)
    }
}