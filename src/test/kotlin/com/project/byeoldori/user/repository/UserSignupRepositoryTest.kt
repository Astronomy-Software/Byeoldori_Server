package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.EmailVerificationToken
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 버려진 가입 덮어쓰기(조건부 UPDATE)와 인증 메일 재발송 간격 쿼리를 실제 JPA(H2 MySQL 모드)로 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSignupRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val users: UserRepository,
    private val emailTokens: EmailVerificationTokenRepository,
) {
    private fun pending(n: Int, configure: User.() -> Unit = {}) = em.persist(
        User(email = "su$n@test.com", passwordHash = "oldHash", name = "옛이름", phone = "010", nickname = "옛닉$n")
            .apply { birthdate = LocalDate.of(1990, 1, 1) }
            .apply(configure)
    )

    @Test
    fun `읽은 해시가 그대로인 버려진 가입만 덮어쓰고, 두 번째 덮어쓰기는 0 건`() {
        val u = pending(1)
        em.flush(); em.clear()

        assertThat(users.overwriteAbandonedSignup(u.id, "oldHash", "h1", "새이름", "01011112222", null)).isEqualTo(1)
        // 동시에 들어온 다른 가입: 같은 옛 해시를 조건으로 걸었지만 이미 바뀌었다
        assertThat(users.overwriteAbandonedSignup(u.id, "oldHash", "h2", "남의이름", "01099998888", "닉")).isZero()
        em.clear()

        val saved = users.findById(u.id).get()
        assertThat(saved.passwordHash).isEqualTo("h1")
        assertThat(saved.name).isEqualTo("새이름")
        assertThat(saved.phone).isEqualTo("01011112222")
        assertThat(saved.nickname).isNull()
        assertThat(saved.birthdate).isNull()
    }

    @Test
    fun `그사이 인증·로그인·소셜 연결·탈퇴된 계정은 덮어쓰지 않는다`() {
        val verified = pending(2) { emailVerified = true }
        val loggedIn = pending(3) { lastLoginAt = LocalDateTime.now() }
        val social = pending(4) { provider = "kakao"; providerId = "k4" }
        val deleted = pending(5) { deletedAt = LocalDateTime.now() }
        em.flush(); em.clear()

        listOf(verified, loggedIn, social, deleted).forEach {
            assertThat(users.overwriteAbandonedSignup(it.id, "oldHash", "new", "n", "p", null)).isZero()
        }
    }

    @Test
    fun `재발송 간격 쿼리는 최근에 발급한 미사용 링크만 본다`() {
        val u = pending(6)
        val now = LocalDateTime.now()
        // 30초 전에 발급(만료 = 발급 + 24시간)
        em.persist(EmailVerificationToken(user = u, expiresAt = now.minusSeconds(30).plusHours(24)))
        em.flush(); em.clear()

        assertThat(emailTokens.existsUnusedExpiringAfter(u.id, now.plusHours(24).minusSeconds(60))).isTrue()  // 60초 안에 보냄
        assertThat(emailTokens.existsUnusedExpiringAfter(u.id, now.plusHours(24).minusSeconds(10))).isFalse() // 10초 안에는 아님
    }
}
