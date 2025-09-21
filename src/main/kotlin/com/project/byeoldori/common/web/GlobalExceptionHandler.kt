package com.project.byeoldori.common.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ApiError(val message: String, val code: String? = null)

@ControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val msg = ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.badRequest().body(ApiError(msg, "VALIDATION_ERROR"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(ex: IllegalArgumentException) =
        ResponseEntity.badRequest().body(ApiError(ex.message ?: "Invalid request", "BAD_REQUEST"))

    @ExceptionHandler(Exception::class)
    fun handleOthers(ex: Exception) =
        ResponseEntity.internalServerError().body(ApiError("Internal error", "INTERNAL_ERROR"))
}