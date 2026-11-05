package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * refresh_tokens 다중 세션 매핑을 실제 JPA(H2 MySQL 모드)로 검증한다.
 * 사용자당 여러 행이 가능하고, token_hash 는 UNIQUE 이며, 조회·정리 쿼리가 해당 행만 건드린다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val repo: RefreshTokenRepository,
) {
    private val now = LocalDateTime.now()

    private fun user(n: Int) = em.persist(
        User(email = "rt$n@test.com", passwordHash = "x", name = "rt$n", phone = "010")
    )

    private fun token(u: User, hash: String, expiresAt: LocalDateTime = now.plusDays(14), revokedAt: LocalDateTime? = null) =
        em.persist(RefreshToken(user = u, tokenHash = hash.padEnd(64, '0'), expiresAt = expiresAt, revokedAt = revokedAt))

    @Test
    fun `한 사용자가 여러 세션 행을 가질 수 있고 해시로 그 행만 찾는다`() {
        val u = user(1)
        token(u, "a")
        token(u, "b")
        em.flush(); em.clear()

        assertThat(repo.findAllByUserIdNewestFirst(u.id).map { it.tokenHash.take(1) }).containsExactly("b", "a")
        assertThat(repo.findByTokenHashForUpdate("b".padEnd(64, '0')).get().user.id).isEqualTo(u.id)
        assertThat(repo.findByTokenHashForUpdate("z".padEnd(64, '0'))).isEmpty()
    }

    @Test
    fun `같은 토큰 해시는 두 행에 들어갈 수 없다`() {
        val u = user(2)
        token(u, "dup")
        assertThatThrownBy { token(u, "dup"); em.flush() }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `정리 쿼리는 만료·폐기된 행만, 로그아웃은 그 해시의 행만 지운다`() {
        val u = user(3)
        val other = user(4)
        token(u, "live")
        token(u, "expired", expiresAt = now.minusSeconds(1))
        token(u, "revoked", revokedAt = now)
        token(other, "otherexpired", expiresAt = now.minusSeconds(1))
        em.flush(); em.clear()

        assertThat(repo.deleteInactiveByUserId(u.id, now)).isEqualTo(2)
        assertThat(repo.findAllByUserIdNewestFirst(u.id).map { it.tokenHash.take(4) }).containsExactly("live")
        assertThat(repo.findAllByUserIdNewestFirst(other.id)).hasSize(1)

        assertThat(repo.deleteByUserIdAndTokenHash(other.id, "live".padEnd(64, '0'))).isZero()
        assertThat(repo.deleteByUserIdAndTokenHash(u.id, "live".padEnd(64, '0'))).isEqualTo(1)
        assertThat(repo.findAllByUserIdNewestFirst(u.id)).isEmpty()
    }
}
