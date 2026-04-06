package com.project.byeoldori.notification.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["fcm.enabled"], havingValue = "true")
class FirebaseConfig {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isNotEmpty()) {
            return FirebaseApp.getInstance()
        }

        val credentialsJson = System.getenv("FIREBASE_CREDENTIALS_JSON")
        val credentials = if (!credentialsJson.isNullOrBlank()) {
            logger.info("Firebase: 환경변수 FIREBASE_CREDENTIALS_JSON 로드")
            GoogleCredentials.fromStream(credentialsJson.byteInputStream())
        } else {
            logger.warn("Firebase: FIREBASE_CREDENTIALS_JSON 없음, Application Default Credentials 사용")
            GoogleCredentials.getApplicationDefault()
        }

        val options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build()

        return FirebaseApp.initializeApp(options)
    }
}
