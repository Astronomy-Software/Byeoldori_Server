package com.project.byeoldori.community.common.exception

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

    private fun reasonPhraseOf(code: Int): String =
        HttpStatus.resolve(code)?.reasonPhrase ?: "Error"

    @ExceptionHandler(ResponseStatusException::class)
    fun handleRSE(ex: ResponseStatusException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val code = ex.statusCode.value()
        val body = ApiError(
            status = code,
            error = reasonPhraseOf(code),
            message = ex.reason ?: "요청을 처리할 수 없습니다.",
            path = req.requestURI
        )
        return ResponseEntity.status(code).body(body)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException, req: HttpServletRequest)
            : ResponseEntity<ApiError> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        val body = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "요청 값이 올바르지 않습니다.",
            path = req.requestURI,
            details = fieldErrors
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, req: HttpServletRequest)
            : ResponseEntity<ApiError> {
        val details = ex.constraintViolations.associate { v ->
            // ex: "create.arg0" 또는 "list.size"
            (v.propertyPath?.toString() ?: "param") to (v.message ?: "invalid")
        }
        val body = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "요청 파라미터가 올바르지 않습니다.",
            path = req.requestURI,
            details = details
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(BindException::class)
    fun handleBindException(ex: BindException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val details = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        val body = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "요청 바인딩에 실패했습니다.",
            path = req.requestURI,
            details = details
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, req: HttpServletRequest)
            : ResponseEntity<ApiError> {
        val body = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "필수 파라미터가 누락되었습니다: ${ex.parameterName}",
            path = req.requestURI
        )
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, req: HttpServletRequest)
            : ResponseEntity<ApiError> {
        // 예: 좋아요 중복 등 제약 위반 → 상황에 따라 409 또는 400
        val status = HttpStatus.CONFLICT
        val body = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = "데이터 제약 조건을 위반했습니다.",
            path = req.requestURI
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandled(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiError> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        val body = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = "서버 내부 오류가 발생했습니다.",
            path = req.requestURI
        )
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArg(ex: IllegalArgumentException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiError(s.value(), s.reasonPhrase, ex.message ?: "잘못된 요청입니다.", req.requestURI)
        )
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiError(s.value(), s.reasonPhrase, "필수 파일 파라미터가 누락되었습니다: ${ex.requestPartName}", req.requestURI)
        )
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(ex: MaxUploadSizeExceededException, req: HttpServletRequest): ResponseEntity<ApiError> {
        val s = HttpStatus.PAYLOAD_TOO_LARGE
        return ResponseEntity.status(s).body(
            ApiError(s.value(), s.reasonPhrase, "파일이 너무 큽니다.", req.requestURI)
        )
    }

    @ExceptionHandler(MultipartException::class, IOException::class)
    fun handleMultipart(ex: Exception, req: HttpServletRequest): ResponseEntity<ApiError> {
        val s = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(s).body(
            ApiError(s.value(), s.reasonPhrase, "멀티파트 요청이 올바르지 않습니다.", req.requestURI)
        )
    }
}