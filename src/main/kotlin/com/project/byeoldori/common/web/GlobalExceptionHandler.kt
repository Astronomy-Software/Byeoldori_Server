package com.project.byeoldori.common.web

import com.project.byeoldori.common.exception.ByeoldoriException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 직접 정의한 ByeoldoriException을 처리
    @ExceptionHandler(ByeoldoriException::class)
    fun handleByeoldoriException(e: ByeoldoriException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("Custom Exception: code={}, status={}, message={}", e.errorCode.name, e.errorCode.status, e.message)
        return ResponseEntity.status(e.errorCode.status)
            .body(ApiResponse.fail(message = e.message))
    }

    // DTO의 @Valid 유효성 검증에 실패했을 때 발생하는 예외를 처리
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값이 올바르지 않습니다."
        log.warn("Validation Exception: {}", message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(message = message))
    }

    // 위에서 처리하지 못한 모든 예외를 처리하는 최후의 보루
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.error("Unhandled Exception at ${req.requestURI}", e)
        return ResponseEntity.internalServerError()
            .body(ApiResponse.fail(message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
    }
}