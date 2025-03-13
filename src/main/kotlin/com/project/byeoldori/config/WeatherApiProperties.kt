package com.project.byeoldori.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "weather.api")
data class WeatherApiProperties(
    var key: String = ""
)
