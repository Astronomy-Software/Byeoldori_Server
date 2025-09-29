package com.project.byeoldori.common.web

import com.project.byeoldori.forecast.utils.region.GeoBounds
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

data class ErrorInfo(
    val code: String? = null,
    val path: String? = null,
    val timestamp: Instant = Instant.now(),
    val details: Any? = null
)

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 한국 서비스 영역 밖: 418
    @ExceptionHandler(OutOfServiceAreaException::class)
    fun handleOutOfServiceArea(
        ex: OutOfServiceAreaException,
        req: HttpServletRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val status = HttpStatus.I_AM_A_TEAPOT
        val msg = "한국 내 좌표만 지원합니다. (위도: ${GeoBounds.LAT_MIN}~${GeoBounds.LAT_MAX}, 경도: ${GeoBounds.LON_MIN}~${GeoBounds.LON_MAX})"
        return ResponseEntity.status(status)
            .body(ApiResponse.fail(message = ex.message ?: msg, data = null))
    }

    // 잘못된 요청: 400
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun handleBadRequest(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.BAD_REQUEST
        val body = ApiResponse.fail(
            message = ex.message ?: "잘못된 요청입니다.",
            data = ErrorInfo(code = "BAD_REQUEST", path = req.requestURI)
        )
        return ResponseEntity.status(status).body(body)
    }

    // 허용되지 않은 메서드: 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.METHOD_NOT_ALLOWED
        val body = ApiResponse.fail(
            message = "허용되지 않은 메서드입니다: ${ex.method}",
            data = ErrorInfo(code = "METHOD_NOT_ALLOWED", path = req.requestURI)
        )
        return ResponseEntity.status(status).body(body)
    }

    // 최종 안전망: 500
    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        log.error("Unhandled exception at ${req.requestURI}", ex)
        val body = ApiResponse.fail(
            message = "서버 내부 오류가 발생했습니다.",
            data = ErrorInfo(code = "INTERNAL_ERROR", path = req.requestURI)
        )
        return ResponseEntity.status(status).body(body)
    }
}