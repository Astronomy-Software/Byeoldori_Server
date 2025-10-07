package com.project.byeoldori.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.common.web.ApiResponse
import com.project.byeoldori.security.JwtAuthenticationFilter
import com.project.byeoldori.security.OAuth2SuccessHandler
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
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
class SecurityConfig(
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper
){
    // 로그인 없이 접근해야만 하는 최소한의 경로
    companion object {
        private val PUBLIC_URLS = arrayOf(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/auth/**",
            "/reset-password"
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
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.characterEncoding = StandardCharsets.UTF_8.name()
                    objectMapper.writeValue(response.writer, ApiResponse.fail<Unit>("사용자 인증이 필요합니다."))
                }
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    // 위에서 정의한 PUBLIC_URLS 경로들은 인증 없이 허용
                    .requestMatchers(*PUBLIC_URLS).permitAll()

                    // 그 외의 모든 요청은 반드시 인증(로그인)을 요구
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .oauth2Login { oauth ->
                oauth.successHandler(oAuth2SuccessHandler)
            }

        return http.build()
    }

    //프론트엔드가 분리되어 있을 경우 사용
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:8080", "http://43.202.235.27",
                "http://byeoldori-app.duckdns.org", "https://byeoldori-app.duckdns.org") // 실제 운영시엔 origin을 제한
            allowedMethods = listOf("GET", "POST", "PUT", "PATH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}