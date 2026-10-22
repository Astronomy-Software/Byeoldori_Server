package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.RefreshToken
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    // 재발급: 제시된 토큰의 행만 잠그고 회전한다(같은 토큰으로 동시에 들어온 요청은 하나만 통과)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(tokenHash: String): Optional<RefreshToken>

    // 세션 수 상한 계산용(최근 로그인 순)
    @Query("select t from RefreshToken t where t.user.id = :userId order by t.id desc")
    fun findAllByUserIdNewestFirst(userId: Long): List<RefreshToken>

    // 로그아웃: 현재 기기의 세션만 지운다
    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId and t.tokenHash = :tokenHash")
    fun deleteByUserIdAndTokenHash(userId: Long, tokenHash: String): Int

    // 로그인 때 만료·폐기된 행을 정리해 행이 계속 쌓이지 않게 한다
    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId and (t.expiresAt < :now or t.revokedAt is not null)")
    fun deleteInactiveByUserId(userId: Long, now: LocalDateTime): Int

    // 비밀번호 변경·재설정·탈퇴: 모든 기기의 세션을 지운다
    @Modifying
    @Query("delete from RefreshToken t where t.user.id = :userId")
    fun deleteByUserId(userId: Long): Int
}
