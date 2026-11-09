package com.project.byeoldori.forecast.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.WebClientRequestException
import java.io.IOException
import java.net.URI

/** 로그 한 줄 설명: 메시지가 null 인 예외도 가장 안쪽 원인을 보여 준다("건너뜀: null" 방지). */
class KmaFailuresDescribeTest {

    @Test
    fun `메시지가 없는 바깥 예외는 가장 안쪽 원인 이름과 메시지를 붙인다`() {
        val root = IOException("Connection prematurely closed BEFORE response")
        val wrapped = WebClientRequestException(RuntimeException(root), HttpMethod.GET, URI("https://x"), HttpHeaders())

        val text = KmaFailures.describe(wrapped)

        assertThat(text).isEqualTo("WebClientRequestException ← IOException: Connection prematurely closed BEFORE response")
        assertThat(text).doesNotContain("null")
    }

    @Test
    fun `원인이 없으면 예외 이름과 메시지만, 메시지도 없으면 이름만`() {
        assertThat(KmaFailures.describe(IllegalStateException("격자 비었음"))).isEqualTo("IllegalStateException: 격자 비었음")
        assertThat(KmaFailures.describe(NullPointerException())).isEqualTo("NullPointerException")
    }
}
