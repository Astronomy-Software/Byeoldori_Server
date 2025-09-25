package com.project.byeoldori.forecast.config

import io.netty.channel.ChannelOption
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
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(15))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(15))
                    .addHandlerLast(WriteTimeoutHandler(15))
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
