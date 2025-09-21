package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByUserId(userId: Long): Optional<RefreshToken>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.user.id = :userId")
    fun findByUserIdForUpdate(userId: Long): Optional<RefreshToken>

    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId")
    fun deleteByUserId(userId: Long): Int
}
