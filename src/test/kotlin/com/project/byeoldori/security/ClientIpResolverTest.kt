package com.project.byeoldori.security

import com.project.byeoldori.security.ClientIpResolver.Companion.CLIENT_IP_HEADER
import com.project.byeoldori.security.ClientIpResolver.Companion.PROXY_SECRET_HEADER
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.filter.ForwardedHeaderFilter

/**
 * 레이트리밋 IP 는 웹 프록시가 공유 비밀을 맞게 보냈을 때만 X-Byeoldori-Client-IP 를 믿고,
 * 그 밖에는 nginx 가 넣은 X-Forwarded-For 맨 오른쪽 값을 쓴다(맨 왼쪽은 클라이언트가 위조 가능).
 */
class ClientIpResolverTest {

    private val secret = "test-proxy-secret"

    private fun request(xff: String? = null, block: MockHttpServletRequest.() -> Unit = {}) =
        MockHttpServletRequest("POST", "/auth/login").apply {
            remoteAddr = "172.18.0.1"
            if (xff != null) addHeader("X-Forwarded-For", xff)
            block()
        }

    @Test
    fun `비밀 없이 위조한 X-Forwarded-For 맨 왼쪽 값은 무시하고 nginx 가 붙인 맨 오른쪽 값을 쓴다`() {
        val resolver = ClientIpResolver(secret)
        assertThat(resolver.resolve(request("6.6.6.6, 1.2.3.4"))).isEqualTo("1.2.3.4")
        assertThat(resolver.resolve(request("1.2.3.4"))).isEqualTo("1.2.3.4")
    }

    @Test
    fun `비밀 없이 보낸 클라이언트 IP 헤더는 무시한다`() {
        val req = request("1.2.3.4") { addHeader(CLIENT_IP_HEADER, "9.9.9.9") }
        assertThat(ClientIpResolver(secret).resolve(req)).isEqualTo("1.2.3.4")
    }

    @Test
    fun `비밀이 맞으면 웹 프록시가 넘긴 클라이언트 IP 를 쓴다`() {
        val req = request("6.6.6.6, 104.28.0.1") {
            addHeader(PROXY_SECRET_HEADER, secret)
            addHeader(CLIENT_IP_HEADER, "203.0.113.7")
        }
        assertThat(ClientIpResolver(secret).resolve(req)).isEqualTo("203.0.113.7")

        val v6 = request("104.28.0.1") {
            addHeader(PROXY_SECRET_HEADER, secret)
            addHeader(CLIENT_IP_HEADER, "2001:db8::1")
        }
        assertThat(ClientIpResolver(secret).resolve(v6)).isEqualTo("2001:db8::1")
    }

    @Test
    fun `비밀이 틀리면 클라이언트 IP 헤더를 무시한다`() {
        val resolver = ClientIpResolver(secret)
        listOf("wrong", "", "$secret-x", secret.dropLast(1)).forEach { wrong ->
            val req = request("104.28.0.1") {
                addHeader(PROXY_SECRET_HEADER, wrong)
                addHeader(CLIENT_IP_HEADER, "203.0.113.7")
            }
            assertThat(resolver.resolve(req)).isEqualTo("104.28.0.1")
        }
    }

    @Test
    fun `서버 비밀이 비어 있으면 어떤 값을 보내도 클라이언트 IP 헤더를 믿지 않는다`() {
        listOf("", "   ").forEach { blank ->
            val resolver = ClientIpResolver(blank)
            listOf("", "   ", "anything").forEach { sent ->
                val req = request("104.28.0.1") {
                    addHeader(PROXY_SECRET_HEADER, sent)
                    addHeader(CLIENT_IP_HEADER, "203.0.113.7")
                }
                assertThat(resolver.resolve(req)).isEqualTo("104.28.0.1")
            }
        }
    }

    @Test
    fun `비밀이 맞아도 IP 형식이 아니면 버리고 nginx 값을 쓴다`() {
        val req = request("104.28.0.1") {
            addHeader(PROXY_SECRET_HEADER, secret)
            addHeader(CLIENT_IP_HEADER, "evil; DROP")
        }
        assertThat(ClientIpResolver(secret).resolve(req)).isEqualTo("104.28.0.1")
    }

    @Test
    fun `맨 오른쪽 값이 IP 형식이 아니면 왼쪽으로 넘어가지 않고 remoteAddr 를 쓴다`() {
        val resolver = ClientIpResolver(secret)
        assertThat(resolver.resolve(request("1.2.3.4, garbage"))).isEqualTo("172.18.0.1")
        assertThat(resolver.resolve(request())).isEqualTo("172.18.0.1")
    }

    @Test
    fun `X-Forwarded-For 헤더가 여러 줄이면 마지막 줄의 마지막 값을 쓴다`() {
        val req = request("6.6.6.6") { addHeader("X-Forwarded-For", "7.7.7.7, 1.2.3.4") }
        assertThat(ClientIpResolver(secret).resolve(req)).isEqualTo("1.2.3.4")
    }

    @Test
    fun `ForwardedHeaderFilter 가 감싼 요청도 원본 헤더로 판단해 Forwarded 위조에 속지 않는다`() {
        // server.forward-headers-strategy=framework 와 같은 필터. 감싼 요청은 X-Forwarded-For 가 지워지고
        // remoteAddr 가 클라이언트가 보낸 Forwarded/X-Forwarded-For 맨 왼쪽 값으로 바뀐다.
        val raw = request("6.6.6.6, 1.2.3.4") { addHeader("Forwarded", "for=8.8.8.8") }
        var wrapped: HttpServletRequest? = null
        ForwardedHeaderFilter().doFilter(raw, MockHttpServletResponse(), FilterChain { req, _ -> wrapped = req as HttpServletRequest })

        assertThat(wrapped!!.remoteAddr).isEqualTo("8.8.8.8")
        assertThat(wrapped!!.getHeader("X-Forwarded-For")).isNull()
        assertThat(ClientIpResolver(secret).resolve(wrapped!!)).isEqualTo("1.2.3.4")
    }
}
