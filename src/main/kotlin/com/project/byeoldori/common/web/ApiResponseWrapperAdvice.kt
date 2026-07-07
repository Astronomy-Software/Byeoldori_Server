package com.project.byeoldori.common.web

import org.springframework.core.MethodParameter
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.ResourceHttpMessageConverter
import org.springframework.http.converter.ResourceRegionHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * com.project.byeoldori 패키지의 REST 컨트롤러 성공 응답을 ApiResponse<T> 봉투로 자동 통일한다.
 *
 * - 이미 ApiResponse 이면 그대로 둔다(이중 래핑 방지).
 * - GlobalExceptionHandler(에러 응답)도 이미 ApiResponse 이므로 감싸지 않는다.
 * - String / byte[] / Resource(actuator, swagger, 파일 다운로드, 리다이렉트 문자열 등)는 감싸지 않는다.
 * - ProblemDetail(RFC 7807 표준 에러)은 감싸지 않는다.
 * - basePackages 로 우리 컨트롤러에만 적용되어 springdoc/actuator 엔드포인트는 대상에서 제외된다.
 */
@RestControllerAdvice(basePackages = ["com.project.byeoldori"])
class ApiResponseWrapperAdvice : ResponseBodyAdvice<Any?> {

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>
    ): Boolean {
        // 문자열/바이트/리소스 변환기가 선택된 경우 JSON 봉투로 감싸지 않는다.
        if (StringHttpMessageConverter::class.java.isAssignableFrom(converterType)) return false
        if (ByteArrayHttpMessageConverter::class.java.isAssignableFrom(converterType)) return false
        if (ResourceHttpMessageConverter::class.java.isAssignableFrom(converterType)) return false
        if (ResourceRegionHttpMessageConverter::class.java.isAssignableFrom(converterType)) return false
        return true
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse
    ): Any? {
        // 이미 봉투이거나 감싸면 안 되는 타입은 그대로 통과시킨다.
        if (body is ApiResponse<*>) return body
        if (body is ProblemDetail) return body
        if (body is Resource) return body
        if (body is ByteArray) return body
        if (body is String) return body
        return ApiResponse.ok(body)
    }
}
