package com.project.byeoldori.user.service

import jakarta.mail.MessagingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // application.properties에 설정된 호스트 URL (http://localhost:8080)
    @Value("\${app.host-url}")
    private lateinit var hostUrl: String

    fun sendVerificationEmail(to: String, token: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(to)
            helper.setSubject("[별도리] 이메일 인증을 완료해주세요")
            helper.setText(buildEmailBody(token), true)

            mailSender.send(message)
        } catch (e: MessagingException) {
            logger.error("이메일 전송 실패: {}", e.message)
            throw RuntimeException("이메일 전송에 실패했습니다.")
        }
    }

    // HTML 이메일 본문 생성
    private fun buildEmailBody(token: String): String {
        val verificationUrl = UriComponentsBuilder
            .fromHttpUrl(hostUrl)
            .path("/auth/verify-email")
            .queryParam("token", token)
            .build()
            .toUriString()

        val context = Context().apply {
            setVariable("verificationUrl", verificationUrl)
        }

        return templateEngine.process("email-verification", context)
    }

    // 비밀번호 재설정 시 메일 전송
    fun sendEmail(to: String, subject: String, body: String) {
        val message = SimpleMailMessage().apply {
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        mailSender.send(message)
    }
}