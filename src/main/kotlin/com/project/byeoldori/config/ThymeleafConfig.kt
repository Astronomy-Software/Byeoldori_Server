package com.project.byeoldori.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.thymeleaf.TemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templatemode.TemplateMode

@Configuration
class ThymeleafConfig {

    @Bean
    fun templateEngine(): TemplateEngine {
        val templateResolver = ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "UTF-8"
            templateMode = TemplateMode.HTML
            order = 1
        }
        return TemplateEngine().apply {
            setTemplateResolver(templateResolver)
        }
    }
}