package com.project.byeoldori.config

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GoogleVerifierConfig(
    @Value("\${spring.security.oauth2.client.registration.google.client-id}")
    private val clientId: String
) {
    @Bean
    fun googleIdTokenVerifier(): GoogleIdTokenVerifier {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        return GoogleIdTokenVerifier.Builder(transport, jsonFactory)
            .setAudience(listOf(clientId))
            .build()
    }
}