package com.project.byeoldori.forecast.config

import io.netty.channel.ChannelOption
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
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)   // TCP 연결 10s
            .responseTimeout(Duration.ofSeconds(60))                  // 응답 수신 60s (격자 340KB 고려)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl("https://apihub.kma.go.kr/api/typ01")
            .defaultHeader("Content-Type", "application/json")
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(524288)
            }
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
