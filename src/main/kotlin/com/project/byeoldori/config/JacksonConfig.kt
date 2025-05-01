package com.project.byeoldori.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig(
    private val objectMapper: ObjectMapper
) {
    @PostConstruct
    fun setup() {
        // Java 8 날짜/시간 모듈 등록
        objectMapper.registerModule(JavaTimeModule())

        // Timestamp가 아닌 ISO 형식으로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}
