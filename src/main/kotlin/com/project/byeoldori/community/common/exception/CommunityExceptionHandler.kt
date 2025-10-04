package com.project.byeoldori.community.common.exception

import com.project.byeoldori.common.web.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.server.ResponseStatusException
import java.io.IOException

@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice("com.project.byeoldori.community")
class CommunityExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleRSE(ex: ResponseStatusException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val body = ApiResponse.fail<Unit>(
            message = ex.reason ?: "요청을 처리할 수 없습니다."
        )
        return ResponseEntity.status(ex.statusCode).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<Map<String, String?>>> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        val body = ApiResponse.fail(
            message = "요청 값이 올바르지 않습니다.",
            data = fieldErrors
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<Map<String, String?>>> {
        val details = ex.constraintViolations.associate { v ->
            (v.propertyPath?.toString() ?: "param") to v.message
        }
        val body = ApiResponse.fail(
            message = "요청 파라미터가 올바르지 않습니다.",
            data = details
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException, req: HttpServletRequest): ResponseEntity<ApiResponse<Map<String, String?>>> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        val body = ApiResponse.fail(
            message = "요청 바인딩에 실패했습니다.",
            data = details
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<Unit>> {
        val body = ApiResponse.fail<Unit>(
            message = "필수 파라미터가 누락되었습니다: ${ex.parameterName}"
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, req: HttpServletRequest)
            : ResponseEntity<ApiResponse<Unit>> {
        val status = HttpStatus.CONFLICT
        val body = ApiResponse.fail<Unit>(
            message = "데이터 제약 조건을 위반했습니다."
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandled(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val body = ApiResponse.fail<Unit>(
            message = "서버 내부 오류가 발생했습니다."
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(ex: IllegalArgumentException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiResponse.fail(ex.message ?: "잘못된 요청입니다.")
        )
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiResponse.fail("필수 파일 파라미터가 누락되었습니다: ${ex.requestPartName}")
        )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(ex: MaxUploadSizeExceededException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val s = HttpStatus.PAYLOAD_TOO_LARGE
        return ResponseEntity.status(s).body(
            ApiResponse.fail("파일이 너무 큽니다.")
        )
    }

    @ExceptionHandler(MultipartException::class, IOException::class)
    fun handleMultipart(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiResponse.fail("멀티파트 요청이 올바르지 않습니다.")
        )
    }
}