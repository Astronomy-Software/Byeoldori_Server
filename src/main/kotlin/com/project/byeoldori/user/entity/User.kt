package com.project.byeoldori.user.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonProperty.Access
import com.project.byeoldori.common.jpa.BaseTimeEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,  // 사용자 고유 ID (자동 생성)

    @Column(nullable = false, unique = true)
    val email: String,  // 사용자 이메일 (중복 불가)

    @Column(nullable = false)
    @JsonProperty(access = Access.WRITE_ONLY)
    var passwordHash: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    var phone: String,

    @Column(nullable = true, unique = true)
    var nickname: String? = null,  // 사용자 닉네임 (중복 불가)

    @Column(nullable = true)
    var birthdate: LocalDate? = null, // 생년월일

    @Column(nullable = false)
    var emailVerified: Boolean = false,

    var lastLoginAt: LocalDateTime? = null,

    @Column(nullable = true, length = 32)
    var provider: String? = null,


    @Column(name = "provider_id", nullable = true, length = 128)
    var providerId: String? = null,


    @Column(nullable = true, length = 1024)
    var profileImageUrl: String? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "role")
    var roles: MutableSet<String> = mutableSetOf("USER")
) : BaseTimeEntity()