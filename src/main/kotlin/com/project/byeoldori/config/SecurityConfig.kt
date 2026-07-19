package com.project.byeoldori.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.security.JwtAuthenticationFilter
import com.project.byeoldori.security.RateLimitFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.nio.charset.StandardCharsets

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val objectMapper: ObjectMapper,
    @org.springframework.beans.factory.annotation.Value("\${cors.allowed-origins}") private val allowedOrigins: List<String>
){
    // 로그인 없이 접근해야만 하는 최소한의 경로
    companion object {
        private val PUBLIC_URLS = arrayOf(
            "/v3/api-docs/**",
            "/auth/**",
            "/reset-password",
            "/actuator/health",
            "/actuator/prometheus",
            "/weather/**",
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource())} // Cors 설정 추가, Cors -> 다른 출처에서 리소스 요청 시 접근 권한을 부여하는 메커니즘
            .csrf { it.disable() } // CSRF 보호 비활성화 (REST API에서 불필요)
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) } // 세션 비활성화
            .exceptionHandling { exceptionHandling ->
                // 인증되지 않은 사용자가 보호된 리소스에 접근 시 401 Unauthorized 응답을 보내도록 설정
                exceptionHandling.authenticationEntryPoint { _, response, _ ->
                    if (!response.isCommitted) {
                        response.status = HttpServletResponse.SC_UNAUTHORIZED
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.characterEncoding = StandardCharsets.UTF_8.name()
                        objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>("사용자 인증이 필요합니다."))
                    }
                }

                // 인가 실패 403 에러
                exceptionHandling.accessDeniedHandler { _, response, _ ->
                    if (!response.isCommitted) {
                        response.status = HttpServletResponse.SC_FORBIDDEN
                        response.contentType = MediaType.APPLICATION_JSON_VALUE
                        response.characterEncoding = StandardCharsets.UTF_8.name()
                        objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>("접근 권한이 없습니다."))
                    }
                }
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    // CORS preflight
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers(*PUBLIC_URLS).permitAll()
                    // 교육 프로그램(천체투영관 감상)은 비로그인도 열람/재생 가능 — 읽기 전용.
                    // 서비스단에서 PUBLISHED 만 익명에게 노출하고, DRAFT/PREVIEW 는 작성자·관리자만 본다.
                    // 작성·수정·발행·삭제 등 변경 계열은 아래 anyRequest().authenticated() 로 보호된다.
                    .requestMatchers(HttpMethod.GET, "/education/programs", "/education/programs/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/education/programs/*/view").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    //프론트엔드가 분리되어 있을 경우 사용
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val origins = allowedOrigins
        val configuration = CorsConfiguration().apply {
            allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}