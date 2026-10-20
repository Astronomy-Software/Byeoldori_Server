package com.project.byeoldori.community.common.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletResponseWrapper
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

/**
 * 업로드 파일(GET /files/ 하위) 응답에 보안 헤더를 붙인다.
 *
 * 사용자가 올린 파일이 api 오리진(또는 웹 프록시 /api/files 로 byeoldori.com 오리진)에서 문서로 열리면
 * SVG·HTML 안의 스크립트가 그 오리진 권한으로 실행된다(저장형 XSS). 업로드 화이트리스트에서 svg 는 뺐지만
 * 이미 저장된 파일도 있으므로 서빙 단계에서도 막는다.
 * - 모든 파일: X-Content-Type-Options: nosniff, CSP sandbox(스크립트·오리진 접근 차단. PDF 뷰어가 막혀 pdf 는 제외)
 * - 래스터 이미지·pdf 외: Content-Disposition: attachment(브라우저에서 열지 않고 내려받기)
 * - svg·html·xml 등 활성 문서: Content-Type 을 application/octet-stream 으로 강제
 *   (웹 프록시는 content-type·content-disposition 만 넘기고 CSP 는 넘기지 않으므로 이것까지 해야 안전)
 */
@Component
class UploadedFileHeaderFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method != "GET" && request.method != "HEAD") return true
        return !pathOf(request).startsWith("/files/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ext = pathOf(request).substringAfterLast('/').substringAfterLast('.', "").lowercase()

        response.setHeader("X-Content-Type-Options", "nosniff")
        if (ext != "pdf") response.setHeader("Content-Security-Policy", "sandbox")
        if (ext !in INLINE_EXT) response.setHeader("Content-Disposition", "attachment")

        val target = if (ext in ACTIVE_CONTENT_EXT) ForcedOctetStreamResponse(response) else response
        filterChain.doFilter(request, target)
    }

    // 디코딩된 경로 기준으로 판단한다(%2E 등 인코딩으로 확장자 검사를 우회하지 못하게)
    private fun pathOf(request: HttpServletRequest): String =
        UrlPathHelper.defaultInstance.getPathWithinApplication(request)

    private class ForcedOctetStreamResponse(response: HttpServletResponse) : HttpServletResponseWrapper(response) {
        override fun setContentType(type: String?) = super.setContentType(OCTET_STREAM)

        override fun setHeader(name: String, value: String?) {
            if (name.equals("Content-Type", ignoreCase = true)) super.setContentType(OCTET_STREAM)
            else super.setHeader(name, value)
        }

        override fun addHeader(name: String, value: String?) {
            if (name.equals("Content-Type", ignoreCase = true)) super.setContentType(OCTET_STREAM)
            else super.addHeader(name, value)
        }
    }

    companion object {
        private const val OCTET_STREAM = MediaType.APPLICATION_OCTET_STREAM_VALUE

        /** 브라우저에서 바로 보여줘도 되는 형식(스크립트 실행 불가). */
        val INLINE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "pdf")

        /** 문서로 열리면 스크립트가 실행될 수 있는 형식. */
        val ACTIVE_CONTENT_EXT = setOf("svg", "svgz", "html", "htm", "xhtml", "xht", "xml", "xsl", "xslt", "js", "mjs")
    }
}
