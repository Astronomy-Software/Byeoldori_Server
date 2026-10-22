package com.project.byeoldori.user.service

import com.project.byeoldori.common.exception.UnauthorizedException
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.LoginRequestDto
import com.project.byeoldori.user.entity.RefreshToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.EmailVerificationTokenRepository
import com.project.byeoldori.user.repository.PasswordResetTokenRepository
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import com.project.byeoldori.user.utils.TokenHasher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.Optional

/**
 * 리프레시 토큰 발급·재발급(회전)·로그아웃 검증. 실제 JwtUtil + 메모리 목록으로 흉내 낸 refresh_tokens.
 * - 로그인마다 세션 행을 따로 만들어 다른 기기 로그인이 기존 세션을 끊지 않는다.
 * - 회전 직후 옛 토큰 재사용, access 토큰, 탈퇴 계정, 만료·폐기 행은 401.
 */
class UserServiceTokenTest {

    private val jwt = JwtUtil("test-secret-key-for-unit-tests-1234567890", 3_600_000, 1_209_600_000)
    private val currentUserResolver = mock(CurrentUserResolver::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val refreshTokenRepo = mock(RefreshTokenRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)

    private val service = UserService(
        publicBaseUrl = "http://localhost:8080/files",
        googleClientId = "g", googleClientSecret = "g",
        kakaoClientId = "k", kakaoClientSecret = "k",
        naverClientId = "n", naverClientSecret = "n",
        currentUserResolver = currentUserResolver,
        storage = mock(StorageService::class.java),
        userRepository = userRepository,
        emailTokenRepo = mock(EmailVerificationTokenRepository::class.java),
        refreshTokenRepo = refreshTokenRepo,
        passwordEncoder = passwordEncoder,
        jwt = jwt,
        emailService = mock(EmailService::class.java),
        cachedUserLookupService = mock(CachedUserLookupService::class.java),
        passwordResetTokenRepo = mock(PasswordResetTokenRepository::class.java),
    )

    // refresh_tokens 테이블 대역(삽입 순서 = id 순서)
    private val rows = mutableListOf<RefreshToken>()
    private val me = User(id = 42, email = "star@gmail.com", passwordHash = "hash", name = "홍길동", phone = "010")
        .apply { emailVerified = true }

    @BeforeEach
    fun setUp() {
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(me))
        `when`(passwordEncoder.matches("pw", "hash")).thenReturn(true)
        `when`(currentUserResolver.getUser()).thenReturn(me)

        `when`(refreshTokenRepo.findByTokenHashForUpdate(anyString())).thenAnswer { inv ->
            Optional.ofNullable(rows.firstOrNull { it.tokenHash == inv.getArgument<String>(0) })
        }
        `when`(refreshTokenRepo.save(any(RefreshToken::class.java))).thenAnswer { inv ->
            inv.getArgument<RefreshToken>(0).also { rows.add(it) }
        }
        `when`(refreshTokenRepo.findAllByUserIdNewestFirst(anyLong())).thenAnswer { inv ->
            rows.filter { it.user.id == inv.getArgument<Long>(0) }.reversed()
        }
        doAnswer { inv -> rows.removeAll(inv.getArgument<Iterable<RefreshToken>>(0).toSet()); null }
            .`when`(refreshTokenRepo).deleteAll(anyIterable())
        `when`(refreshTokenRepo.deleteByUserIdAndTokenHash(anyLong(), anyString())).thenAnswer { inv ->
            val removed = rows.removeIf { it.user.id == inv.getArgument<Long>(0) && it.tokenHash == inv.getArgument<String>(1) }
            if (removed) 1 else 0
        }
    }

    private fun login() = service.login(LoginRequestDto("star@gmail.com", "pw"))

    @Test
    fun `다른 기기에서 로그인해도 먼저 로그인한 기기의 refresh 가 계속 재발급된다`() {
        val pc = login()
        val phone = login()

        assertThat(rows).hasSize(2)
        assertThat(service.reissue(pc.refreshToken).accessToken).isNotBlank()
        assertThat(service.reissue(phone.refreshToken).accessToken).isNotBlank()
    }

    @Test
    fun `재발급은 그 세션의 해시만 회전시키고 회전 직후 옛 refresh 는 거부한다`() {
        val first = login()
        val other = login()
        val otherHash = rows[1].tokenHash

        val renewed = service.reissue(first.refreshToken)

        assertThat(renewed.refreshToken).isNotEqualTo(first.refreshToken)
        assertThat(rows[0].tokenHash).isEqualTo(TokenHasher.sha256Hex(renewed.refreshToken))
        assertThat(rows[0].rotatedAt).isNotNull()
        assertThat(rows[1].tokenHash).isEqualTo(otherHash)
        assertThatThrownBy { service.reissue(first.refreshToken) }.isInstanceOf(UnauthorizedException::class.java)
        assertThat(service.reissue(other.refreshToken).accessToken).isNotBlank()
    }

    @Test
    fun `access 토큰을 refresh 로 보내면 401`() {
        val auth = login()
        assertThatThrownBy { service.reissue(auth.accessToken) }.isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `탈퇴한 계정은 401 이고 토큰 행은 조회하지 않는다`() {
        val auth = login()
        me.deletedAt = LocalDateTime.now()

        assertThatThrownBy { service.reissue(auth.refreshToken) }.isInstanceOf(UnauthorizedException::class.java)
        verify(refreshTokenRepo, never()).findByTokenHashForUpdate(anyString())
    }

    @Test
    fun `폐기됐거나 만료된 행이면 401`() {
        val revoked = login()
        rows[0].revokedAt = LocalDateTime.now()
        assertThatThrownBy { service.reissue(revoked.refreshToken) }.isInstanceOf(UnauthorizedException::class.java)

        val expired = login()
        rows.last().expiresAt = LocalDateTime.now().minusSeconds(1)
        assertThatThrownBy { service.reissue(expired.refreshToken) }.isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `다른 사용자의 세션 행이면 401`() {
        val auth = login()
        val stranger = User(id = 99, email = "x@test.com", passwordHash = "h", name = "x", phone = "010")
        rows[0] = RefreshToken(user = stranger, tokenHash = rows[0].tokenHash, expiresAt = rows[0].expiresAt)

        assertThatThrownBy { service.reissue(auth.refreshToken) }.isInstanceOf(UnauthorizedException::class.java)
    }

    @Test
    fun `로그아웃은 현재 기기 세션만 지우고, 토큰이 없으면 모든 세션을 지운다`() {
        val pc = login()
        val phone = login()

        service.logout(pc.refreshToken)
        assertThat(rows.map { it.tokenHash }).containsExactly(TokenHasher.sha256Hex(phone.refreshToken))
        verify(refreshTokenRepo, never()).deleteByUserId(42)

        service.logout(null)
        verify(refreshTokenRepo).deleteByUserId(42)
    }

    @Test
    fun `세션이 상한을 넘으면 가장 오래된 세션부터 끝낸다`() {
        val oldest = login()
        repeat(10) { login() }

        assertThat(rows).hasSize(10)
        assertThat(rows.map { it.tokenHash }).doesNotContain(TokenHasher.sha256Hex(oldest.refreshToken))
        verify(refreshTokenRepo, org.mockito.Mockito.atLeastOnce())
            .deleteInactiveByUserId(org.mockito.ArgumentMatchers.eq(42L), any(LocalDateTime::class.java) ?: LocalDateTime.MIN)
    }

    @Test
    fun `같은 초에 두 번 발급해도 refresh 토큰이 서로 다르다(jti)`() {
        assertThat(jwt.generateRefreshToken("a@b.c")).isNotEqualTo(jwt.generateRefreshToken("a@b.c"))
    }
}
