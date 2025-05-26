package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findByNameAndPhone(name: String, phone: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
}