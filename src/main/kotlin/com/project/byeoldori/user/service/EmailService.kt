package com.project.byeoldori.user.service

import jakarta.mail.MessagingException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import jakarta.mail.internet.MimeUtility
import jakarta.mail.internet.InternetAddress

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val templateEngine: TemplateEngine
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${app.host-url}")
    private lateinit var hostUrl: String

    @Value("\${spring.mail.username}")
    private lateinit var fromAddress: String

    fun sendEmailVerification(toEmail: String, token: String) {
        sendVerificationEmail(toEmail, token)
    }

    fun sendTemporaryPassword(to: String, name: String, tempPassword: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(to)
            helper.setFrom(InternetAddress(fromAddress, MimeUtility.encodeText("별도리", "UTF-8", "B")))
            helper.setSubject(MimeUtility.encodeText("[별도리] 임시 비밀번호 안내", "UTF-8", "B"))
            helper.setText(buildTemporaryPasswordBody(name, tempPassword), true)

            mailSender.send(message)
        } catch (e: MessagingException) {
            logger.error("임시 비밀번호 메일 전송 실패: {}", e.message)
            throw RuntimeException("이메일 전송에 실패했습니다.")
        }
    }

    private fun buildTemporaryPasswordBody(name: String, tempPassword: String): String {
        val ctx = Context().apply {
            setVariable("name", name)
            setVariable("tempPassword", tempPassword)
            setVariable("loginUrl", hostUrl.trimEnd('/'))
        }
        return templateEngine.process("temporary-password", ctx)
    }

    fun sendVerificationEmail(to: String, token: String) {
        try {
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setTo(to)
            helper.setFrom(InternetAddress(fromAddress, MimeUtility.encodeText("별도리", "UTF-8", "B")))
            helper.setSubject(MimeUtility.encodeText("[별도리] 이메일 인증을 완료해주세요", "UTF-8", "B"))
            helper.setText(buildEmailBody(token), true)

            mailSender.send(message)
        } catch (e: MessagingException) {
            logger.error("이메일 전송 실패: {}", e.message)
            throw RuntimeException("이메일 전송에 실패했습니다.")
        }
    }

    private fun buildEmailBody(token: String): String {
        val verificationUrl = UriComponentsBuilder
            .fromHttpUrl(hostUrl.trimEnd('/'))
            .path("/auth/verify-email")
            .queryParam("token", token)
            .build()
            .encode(java.nio.charset.StandardCharsets.UTF_8)
            .toUriString()

        val context = Context().apply {
            setVariable("verificationUrl", verificationUrl)
        }
        return templateEngine.process("email-verification", context)
    }
}