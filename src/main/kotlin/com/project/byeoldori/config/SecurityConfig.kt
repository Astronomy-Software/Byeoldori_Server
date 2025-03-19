package com.project.byeoldori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { } // Cors 설정 추가, Cors -> 다른 출처에서 리소스 요청 시 접근 권한을 부여하는 메커니즘
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/v3/api-docs",
                    "/webjars/**",
                    // 이곳에 인바운드 설정을 해주어야한다. controller 작성시마다.
                    "/weather/**"
                ).permitAll()  // Swagger URL을 허용
                it.anyRequest().authenticated()
            }
            .csrf { it.disable() } // CSRF 보호 비활성화 (선택)

        return http.build()
    }

    //프론트엔드가 분리되어 있을 경우 사용
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("*") // 실제 운영시엔 origin을 제한
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}

