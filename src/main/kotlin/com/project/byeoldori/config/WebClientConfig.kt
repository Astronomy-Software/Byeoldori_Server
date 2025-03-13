package com.project.byeoldori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class WebClientConfig {
    @Bean
    fun weatherApiClient(): WebClient {
        return WebClient.builder()
            .baseUrl("https://apihub.kma.go.kr/api/typ01")
            .defaultHeader("Content-Type", "application/json")
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(524288)
            } // Data 512kb 까지 전송받을수있게 설정, 기상청 격자데이터가 약 340kb임
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
