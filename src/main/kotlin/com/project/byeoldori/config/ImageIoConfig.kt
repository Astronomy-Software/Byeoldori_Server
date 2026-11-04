package com.project.byeoldori.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import javax.imageio.ImageIO

@Configuration
class ImageIoConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun registerPlugins() {
        // ImageIO 는 처음 쓰일 때의 클래스로더로 플러그인을 찾는다.
        // Spring Boot 실행 jar(중첩 jar)에서도 WebP 리더(imageio-webp)가 확실히 등록되도록 기동 시 한 번 다시 스캔한다.
        ImageIO.scanForPlugins()
        log.info("ImageIO 리더 형식: {}", ImageIO.getReaderFormatNames().map { it.lowercase() }.toSortedSet())
    }
}
