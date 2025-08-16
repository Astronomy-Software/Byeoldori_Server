package com.project.byeoldori.community.common.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Paths

@Configuration
class FileResourceConfig(
    @Value("\${storage.local.base-dir}") private val baseDir: String
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolute = Paths.get(baseDir).toAbsolutePath().toString().replace("\\", "/")
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:$absolute/")
    }
}