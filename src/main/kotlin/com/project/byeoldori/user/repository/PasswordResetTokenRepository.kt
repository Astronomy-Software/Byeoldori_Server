package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.PasswordResetToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.id = :id and t.usedAt is null")
    fun findUsableForUpdate(@Param("id") id: String): Optional<PasswordResetToken>
}