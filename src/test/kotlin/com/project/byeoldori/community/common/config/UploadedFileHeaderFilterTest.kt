package com.project.byeoldori.community.common.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * 업로드 파일 서빙 응답의 보안 헤더 — 저장된 SVG 등이 오리진 문서로 열려 스크립트가 실행되지 않게 한다.
 */
class UploadedFileHeaderFilterTest {

    private val filter = UploadedFileHeaderFilter()

    /** 리소스 핸들러처럼 확장자 기반 Content-Type 을 쓰는 체인으로 필터를 통과시킨다. */
    private fun serve(method: String, uri: String, servedType: String): MockHttpServletResponse {
        val req = MockHttpServletRequest(method, uri)
        val res = MockHttpServletResponse()
        val chain = FilterChain { _, r -> (r as HttpServletResponse).contentType = servedType }
        filter.doFilter(req, res, chain)
        return res
    }

    @Test
    fun `svg 는 octet-stream 으로 강제하고 내려받기·sandbox 를 붙인다`() {
        val res = serve("GET", "/files/attach/2026/10/20/abc.svg", "image/svg+xml")
        assertThat(res.contentType).isEqualTo("application/octet-stream")
        assertThat(res.getHeader("Content-Disposition")).isEqualTo("attachment")
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo("sandbox")
        assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff")
    }

    @Test
    fun `헤더로 Content-Type 을 넣어도 svg 는 octet-stream`() {
        val req = MockHttpServletRequest("GET", "/files/2026/10/20/abc.svg")
        val res = MockHttpServletResponse()
        filter.doFilter(req, res, FilterChain { _, r -> (r as HttpServletResponse).addHeader("Content-Type", "image/svg+xml") })
        assertThat(res.contentType).isEqualTo("application/octet-stream")
    }

    @Test
    fun `대문자 확장자 html 도 같은 처리`() {
        val res = serve("GET", "/files/attach/2026/10/20/abc.HTML", "text/html")
        assertThat(res.contentType).isEqualTo("application/octet-stream")
        assertThat(res.getHeader("Content-Disposition")).isEqualTo("attachment")
    }

    @Test
    fun `래스터 이미지는 그대로 보여주되 sandbox 를 붙인다`() {
        val res = serve("GET", "/files/2026/10/20/abc.png", "image/png")
        assertThat(res.contentType).isEqualTo("image/png")
        assertThat(res.getHeader("Content-Disposition")).isNull()
        assertThat(res.getHeader("Content-Security-Policy")).isEqualTo("sandbox")
    }

    @Test
    fun `pdf 는 뷰어가 막히지 않게 sandbox 없이 inline`() {
        val res = serve("GET", "/files/attach/2026/10/20/doc.pdf", "application/pdf")
        assertThat(res.contentType).isEqualTo("application/pdf")
        assertThat(res.getHeader("Content-Disposition")).isNull()
        assertThat(res.getHeader("Content-Security-Policy")).isNull()
    }

    @Test
    fun `문서·압축 첨부는 내려받기로 서빙한다`() {
        val res = serve("GET", "/files/attach/2026/10/20/a.zip", "application/zip")
        assertThat(res.contentType).isEqualTo("application/zip")
        assertThat(res.getHeader("Content-Disposition")).isEqualTo("attachment")
    }

    @Test
    fun `업로드 API(POST) 와 다른 경로는 건드리지 않는다`() {
        val post = serve("POST", "/files/upload", "application/json")
        assertThat(post.getHeader("Content-Security-Policy")).isNull()
        assertThat(post.getHeader("Content-Disposition")).isNull()

        val other = serve("GET", "/community/FREE/posts", "application/json")
        assertThat(other.getHeader("Content-Security-Policy")).isNull()
    }
}
