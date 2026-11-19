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

    // 세션 목록(최근 로그인 순)
    @Query("select t from RefreshToken t where t.user.id = :userId order by t.id desc")
    fun findAllByUserIdNewestFirst(userId: Long): List<RefreshToken>

    // 세션 수 상한 계산용 id 만(최근 로그인 순)
    @Query("select t.id from RefreshToken t where t.user.id = :userId order by t.id desc")
    fun findIdsByUserIdNewestFirst(userId: Long): List<Long>

    // 상한을 넘은 오래된 세션을 한 번에 지운다. 엔티티를 하나씩 지우면 같은 사용자의 동시 로그인이
    // 같은 행을 먼저 지웠을 때 삭제 행 수가 0 이 되어 StaleStateException(로그인 500)이 났다. 일괄 삭제는 0건이어도 실패하지 않는다.
    @Modifying
    @Query("delete from RefreshToken t where t.id in :ids")
    fun deleteByIdIn(ids: Collection<Long>): Int

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
