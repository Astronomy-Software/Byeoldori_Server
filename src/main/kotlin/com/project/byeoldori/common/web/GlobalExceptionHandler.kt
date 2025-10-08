package com.project.byeoldori.common.web

import com.project.byeoldori.common.exception.ByeoldoriException
import com.project.byeoldori.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingRequestValueException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

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

    @ExceptionHandler(ResponseStatusException::class)
    fun handleRSE(e: ResponseStatusException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val msg = e.reason ?: "요청 처리 중 오류가 발생했습니다."
        log.warn("RSE at {} -> {} : {}", req.requestURI, e.statusCode, msg)
        return ResponseEntity.status(e.statusCode).body(ApiResponse.fail(message = msg))
    }

    // 쿼리, 파라미터 예외 처리
    @ExceptionHandler(jakarta.validation.ConstraintViolationException::class)
    fun handleConstraintViolation(e: jakarta.validation.ConstraintViolationException): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.badRequest().body(ApiResponse.fail(e.message ?: "요청 값이 올바르지 않습니다."))

    // 400 계열 에러(파라미터/헤더 누락, 타임 불일치, JSON 파싱)
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MissingRequestHeaderException::class,
        MissingRequestValueException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class
    )
    fun handleBadRequest(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val msg = e.message ?: "요청 값이 올바르지 않습니다."
        log.warn("BadRequest at {} -> {}", req.requestURI, msg)
        return ResponseEntity.badRequest().body(ApiResponse.fail(message = msg))
    }

    // 토큰 재발급 예외 처리
    @ExceptionHandler(io.jsonwebtoken.JwtException::class)
    fun handleJwt(e: io.jsonwebtoken.JwtException): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(ErrorCode.INVALID_TOKEN.status)
            .body(ApiResponse.fail(ErrorCode.INVALID_TOKEN.message))

    // 파일 크기 업로드 예외 처리
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun handleMaxUpload(e: org.springframework.web.multipart.MaxUploadSizeExceededException)
            = ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status)
        .body(ApiResponse.fail<Unit>(ErrorCode.FILE_TOO_LARGE.message))


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