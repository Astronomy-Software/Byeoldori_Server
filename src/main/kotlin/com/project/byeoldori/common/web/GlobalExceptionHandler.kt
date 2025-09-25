package com.project.byeoldori.common.web

import com.project.byeoldori.forecast.utils.region.GeoBounds
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.hibernate.query.sqm.tree.SqmNode.log
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Instant

data class ErrorInfo(
    val code: String? = null,
    val path: String? = null,
    val timestamp: Instant = Instant.now(),
    val details: Any? = null
)

@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    //한국 서비스 영역 밖: 418 (I_AM_A_TEAPOT)
    @ExceptionHandler(OutOfServiceAreaException::class)
    fun handleOutOfServiceArea(ex: OutOfServiceAreaException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<Unit>> {
        val status = HttpStatus.I_AM_A_TEAPOT
        val msg = "한국 내 좌표만 지원합니다. (위도: ${GeoBounds.LAT_MIN}~${GeoBounds.LAT_MAX}, 경도: ${GeoBounds.LON_MIN}~${GeoBounds.LON_MAX})"
        return ResponseEntity.status(status)
            .body(ApiResponse.fail(message = ex.message ?: msg, data = null))
    }

    // 잘못된 요청류: 400
    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        ConstraintViolationException::class,
        MissingServletRequestParameterException::class,
        HttpMessageNotReadableException::class,
        IllegalArgumentException::class
    )
    fun handleBadRequest(
        ex: Exception,
        req: HttpServletRequest
    ): ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.BAD_REQUEST
        val (code, message) = when (ex) {
            is MethodArgumentNotValidException -> "VALIDATION_ERROR" to
                    ex.bindingResult.fieldErrors.joinToString { "${it.field}: ${it.defaultMessage}" }
            is ConstraintViolationException -> "VALIDATION_ERROR" to
                    ex.constraintViolations.joinToString { "${it.propertyPath}: ${it.message}" }
            is MissingServletRequestParameterException -> "MISSING_PARAMETER" to
                    "Missing parameter: ${ex.parameterName}"
            is HttpMessageNotReadableException -> "MALFORMED_BODY" to
                    "Malformed request body"
            is IllegalArgumentException -> "BAD_REQUEST" to
                    (ex.message ?: "Invalid request")
            else -> "BAD_REQUEST" to "Bad request"
        }
        val body = ApiResponse.fail(
            message = message,
            data = ErrorInfo(code = code, path = req.requestURI)
        )
        return ResponseEntity.status(status).body(body)
    }

    // 지원하지 않는 HTTP 메서드: 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        req: HttpServletRequest
    ): ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.METHOD_NOT_ALLOWED
        val supported: List<String> =
            ex.supportedHttpMethods?.map { it.name() }
                ?: ex.supportedMethods?.toList()
                ?: emptyList()
        val body = ApiResponse.fail(
            message = "Method not allowed: ${ex.method}",
            data = ErrorInfo(
                code = "METHOD_NOT_ALLOWED",
                path = req.requestURI,
                details = mapOf("supported" to supported)
            )
        )
        return ResponseEntity.status(status).body(body)
    }

    // 최종 안전망: 500
    @ExceptionHandler(Exception::class)
    fun handleUnknown(
        ex: Exception,
        req: HttpServletRequest
    ): ResponseEntity<ApiResponse<ErrorInfo>> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        log.error("Unhandled exception at ${req.requestURI}", ex)
        val body = ApiResponse.fail(
            message = "Internal error",
            data = ErrorInfo(code = "INTERNAL_ERROR", path = req.requestURI)
        )
        return ResponseEntity.status(status).body(body)
    }
}