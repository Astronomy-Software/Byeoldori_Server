package com.project.byeoldori.user.dto

import com.project.byeoldori.user.entity.User
import java.time.LocalDate
import java.time.LocalDateTime

data class UserMeResponseDto(
    val id: Long,
    val email: String,
    val name: String,
    val phone: String,
    val nickname: String? = null,
    val birthdate: LocalDate? = null,
    val emailVerified: Boolean,
    val lastLoginAt: LocalDateTime? = null,
    val roles: Set<String>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val onboardingRequired: Boolean
) {
    companion object {
        fun from(u: User): UserMeResponseDto {
            // 닉네임이 비어있거나, 전화번호 정보가 비어있으면 true로 설정
            val onboardingNeeded = u.nickname.isNullOrBlank() || u.phone.isBlank()

            return UserMeResponseDto(
                id = u.id,
                email = u.email,
                name = u.name,
                phone = u.phone,
                nickname = u.nickname,
                birthdate = u.birthdate,
                emailVerified = u.emailVerified,
                lastLoginAt = u.lastLoginAt,
                roles = u.roles.toSet(),
                createdAt = u.createdAt,
                updatedAt = u.updatedAt,
                onboardingRequired = onboardingNeeded
            )
        }
    }
}