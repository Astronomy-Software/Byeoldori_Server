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
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(u: User) = UserMeResponseDto(
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
            updatedAt = u.updatedAt
        )
    }
}