package com.project.byeoldori.security

import jakarta.servlet.ServletRequestWrapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 레이트리밋에 쓰는 클라이언트 IP.
 *
 * 1. 웹 프록시(Cloudflare Worker)가 공유 비밀을 맞게 보낸 요청이면 X-Byeoldori-Client-IP(CF-Connecting-IP 값)를 쓴다.
 *    웹 트래픽은 전부 Worker IP 로 들어와서, 이걸 안 보면 웹 사용자 전체가 한 버킷을 같이 쓴다.
 * 2. 그 밖에는 nginx 가 본 IP, 즉 X-Forwarded-For 의 맨 오른쪽 값을 쓴다. nginx 가 덮어쓰든($remote_addr)
 *    덧붙이든($proxy_add_x_forwarded_for) 맨 오른쪽은 nginx 가 넣은 값이다. 맨 왼쪽은 클라이언트가 정할 수 있어 믿지 않는다.
 * 3. 비밀(app.proxy.shared-secret)이 비어 있으면 프록시 헤더는 절대 믿지 않는다.
 */
@Component
class ClientIpResolver(
    @Value("\${app.proxy.shared-secret:}") sharedSecret: String
) {

    private val secret: ByteArray? = sharedSecret.trim()
        .takeIf { it.isNotEmpty() }
        ?.toByteArray(StandardCharsets.UTF_8)

    fun resolve(request: HttpServletRequest): String {
        // server.forward-headers-strategy=framework 의 ForwardedHeaderFilter 는 X-Forwarded-For 를 지우고
        // remoteAddr 를 맨 왼쪽 값(또는 클라이언트가 보낸 Forwarded 헤더)으로 바꾼다. 그래서 원본 요청에서 읽는다.
        val raw = originalRequest(request)

        if (isFromWebProxy(raw)) {
            raw.getHeader(CLIENT_IP_HEADER)?.trim()
                ?.takeIf { isValidIp(it) }
                ?.let { return it }
        }

        return lastForwardedFor(raw) ?: raw.remoteAddr
    }

    private fun isFromWebProxy(request: HttpServletRequest): Boolean {
        val expected = secret ?: return false
        val provided = request.getHeader(PROXY_SECRET_HEADER) ?: return false
        // 상수 시간 비교
        return MessageDigest.isEqual(expected, provided.trim().toByteArray(StandardCharsets.UTF_8))
    }

    // 맨 오른쪽 값만 본다. 형식이 틀리면 왼쪽으로 넘어가지 않고(클라이언트 값이므로) remoteAddr 로 폴백.
    private fun lastForwardedFor(request: HttpServletRequest): String? =
        request.getHeaders("X-Forwarded-For")?.toList().orEmpty()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?.takeIf { isValidIp(it) }

    private fun originalRequest(request: HttpServletRequest): HttpServletRequest {
        var current: HttpServletRequest = request
        while (current is ServletRequestWrapper) {
            current = current.request as? HttpServletRequest ?: break
        }
        return current
    }

    private fun isValidIp(ip: String): Boolean =
        IPV4_REGEX.matches(ip) || IPV6_REGEX.matches(ip)

    companion object {
        const val PROXY_SECRET_HEADER = "X-Byeoldori-Proxy-Secret"
        const val CLIENT_IP_HEADER = "X-Byeoldori-Client-IP"

        // 형식 검증용(엄밀한 범위 검증이 아닌 스푸핑성 임의 문자열 차단 목적)
        private val IPV4_REGEX = Regex("^(\\d{1,3})(\\.\\d{1,3}){3}\$")
        private val IPV6_REGEX = Regex("^[0-9A-Fa-f:]+\$")
    }
}
