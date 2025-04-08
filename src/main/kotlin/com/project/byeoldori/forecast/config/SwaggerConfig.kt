package com.project.byeoldori.forecast.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Byeoldori API")
                    .version("1.0.0")
                    .description("별도리 프로젝트 API 문서")
            )
    }
}
