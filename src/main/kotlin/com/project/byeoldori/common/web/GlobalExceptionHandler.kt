package com.project.byeoldori.common.web

import com.project.byeoldori.common.exception.ByeoldoriException
import com.project.byeoldori.common.exception.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingRequestValueException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 직접 정의한 ByeoldoriException을 처리
    @ExceptionHandler(ByeoldoriException::class)
    fun handleByeoldoriException(e: ByeoldoriException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("Custom Exception: code={}, status={}, message={}", e.errorCode.name, e.errorCode.status, e.message)
        return ResponseEntity.status(e.errorCode.status)
            .body(ApiResponse.fail(message = e.message, code = e.errorCode.name))
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

    // 400 계열 에러(파라미터/헤더/multipart 파트 누락, 타임 불일치, JSON 파싱, 깨진 multipart)
    // MaxUploadSizeExceededException 은 MultipartException 하위지만 더 구체적인 아래 핸들러(413)가 우선한다.
    @ExceptionHandler(
        MissingServletRequestParameterException::class,
        MissingRequestHeaderException::class,
        MissingRequestValueException::class,
        MethodArgumentTypeMismatchException::class,
        HttpMessageNotReadableException::class,
        MissingServletRequestPartException::class,
        MultipartException::class
    )
    fun handleBadRequest(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        // 예외 원본 메시지(내부 클래스명/필드 경로 등)는 로깅만 하고 클라이언트에는 고정 메시지 반환
        log.warn("BadRequest at {} -> {}", req.requestURI, e.message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(message = "요청 값이 올바르지 않습니다."))
    }

    // 토큰 재발급 예외 처리
    @ExceptionHandler(io.jsonwebtoken.JwtException::class)
    fun handleJwt(e: io.jsonwebtoken.JwtException): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(ErrorCode.INVALID_TOKEN.status)
            .body(ApiResponse.fail(ErrorCode.INVALID_TOKEN.message, code = ErrorCode.INVALID_TOKEN.name))

    // 파일 크기 업로드 예외 처리
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException::class)
    fun handleMaxUpload(e: org.springframework.web.multipart.MaxUploadSizeExceededException)
            = ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status)
        .body(ApiResponse.fail<Unit>(ErrorCode.FILE_TOO_LARGE.message, code = ErrorCode.FILE_TOO_LARGE.name))


    // DTO의 @Valid 유효성 검증에 실패했을 때 발생하는 예외를 처리
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = e.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "입력값이 올바르지 않습니다."
        log.warn("Validation Exception: {}", message)
        return ResponseEntity.badRequest().body(ApiResponse.fail(message = message))
    }

    // 없는 경로·정적 파일(/files/** 포함) → 404.
    // 이 핸들러가 없으면 아래 Exception 캐치올이 잡아 500 으로 응답했다. 스캐너가 임의 경로를
    // 두드릴 때마다 error 로그와 Grafana 5xx 알림이 오탐으로 쌓이므로 debug 로만 남긴다.
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.debug("Not found: {}", req.requestURI)
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.fail(message = "요청한 리소스를 찾을 수 없습니다."))
    }

    // @PreAuthorize 거부(AuthorizationDeniedException ⊂ AccessDeniedException) → 403.
    // 메서드 보안 예외는 컨트롤러 호출 안에서 나서 SecurityConfig 의 accessDeniedHandler 보다
    // 이 advice 가 먼저 잡는다. 핸들러가 없으면 아래 캐치올이 500 으로 응답했다.
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.warn("Access denied at {} -> {}", req.requestURI, e.message)
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status)
            .body(ApiResponse.fail(ErrorCode.FORBIDDEN.message, code = ErrorCode.FORBIDDEN.name))
    }

    // 지원하지 않는 HTTP 메서드(405)·Content-Type(415). 클라이언트 실수라 500 이 아니라 해당 상태로 응답한다.
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class, HttpMediaTypeNotSupportedException::class)
    fun handleUnsupported(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.debug("Unsupported request at {} -> {}", req.requestURI, e.message)
        // 두 예외 모두 ErrorResponse 라 상태(405/415)와 Allow·Accept 헤더를 그대로 쓴다.
        val er = e as ErrorResponse
        return ResponseEntity.status(er.statusCode).headers(er.headers)
            .body(ApiResponse.fail(message = "지원하지 않는 요청 방식입니다."))
    }

    // 응답 형식 협상 실패(406). 클라이언트가 JSON 을 받지 않겠다고 했으므로 본문 없이 상태만 돌려준다.
    @ExceptionHandler(HttpMediaTypeNotAcceptableException::class)
    fun handleNotAcceptable(e: HttpMediaTypeNotAcceptableException, req: HttpServletRequest): ResponseEntity<Void> {
        log.debug("Not acceptable at {} -> {}", req.requestURI, e.message)
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build()
    }

    // UNIQUE·FK·컬럼 길이 등 DB 제약 위반 → 409. DTO 검증을 빠져나간 경우의 보조 수단이다.
    // 제약 이름·SQL 같은 내부 정보는 로그에만 남기고 클라이언트에는 고정 메시지를 준다.
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(e: DataIntegrityViolationException, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.warn("Data integrity violation at {} -> {}", req.requestURI, e.mostSpecificCause.message)
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.fail(message = "요청을 처리할 수 없습니다. 입력값이 이미 사용 중이거나 허용 범위를 벗어났습니다."))
    }

    // 위에서 처리하지 못한 모든 예외를 처리하는 최후의 보루
    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, req: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> {
        log.error("Unhandled Exception at ${req.requestURI}", e)
        return ResponseEntity.internalServerError()
            .body(ApiResponse.fail(message = "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
    }
}