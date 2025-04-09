package com.project.byeoldori.forecast.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "forecast.retry")
data class RetryProperties(
    var delay: Long = 60,  //(초)
    var attempts: Int = 5  //(재시도 횟수)
)
