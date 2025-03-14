package com.project.byeoldori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
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
}

