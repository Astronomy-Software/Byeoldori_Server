package com.project.byeoldori.forecast.config

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig {
    @Bean
    fun weatherApiClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
            .responseTimeout(Duration.ofSeconds(30))          // 전체 응답 타임아웃 20초
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(30))   // 읽기 타임아웃 20초
                    .addHandlerLast(WriteTimeoutHandler(30))  // 쓰기 타임아웃 20초
            }
            .secure { sslContextSpec ->
                sslContextSpec.sslContext(
                    SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE) // 개발 환경용, 프로덕션에서는 인증서 사용 권장
                        .build()
                ).handshakeTimeout(Duration.ofSeconds(20)) // Handshake 타임아웃 20초로 설정
            }

        return WebClient.builder()
            .baseUrl("https://apihub.kma.go.kr/api/typ01")
            .defaultHeader("Content-Type", "application/json")
            .codecs { c -> c.defaultCodecs().maxInMemorySize(524288) }
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    // 세로운 API 사용시 아래와같이 새로 선언
//    @Bean
//    fun anotherApiClient(): WebClient {
//        return WebClient.builder()
//            .baseUrl("https://api.another.com")
//            .defaultHeader("Authorization", "Bearer example-token")
//            .build()
//    }
}
