package com.project.byeoldori.user.service

import com.project.byeoldori.common.exception.ConflictException
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.EmailVerificationTokenRepository
import com.project.byeoldori.user.repository.PasswordResetTokenRepository
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.lang.reflect.InvocationTargetException
import java.time.LocalDateTime
import java.util.Optional
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * 소셜 로그인 계정 연동 규칙(findOrCreateOAuthUser) 검증. loginWithXxx 는 외부 호출(WebClient)이 있어
 * private 함수를 리플렉션으로 직접 부른다.
 * - 이메일만 같은 로컬 계정은 provider 가 이메일 소유를 확인(verified=true)할 때만 연동한다(Naver 는 항상 null).
 * - 다른 provider, 또는 같은 provider 의 다른 계정(providerId 다름)이면 409 로 연동하지 않는다.
 * - 탈퇴(deletedAt) 행에는 다시 연결하지 않는다.
 */
class UserServiceOAuthLinkTest {

    private val userRepository = mock(UserRepository::class.java)

    private val service = UserService(
        publicBaseUrl = "http://localhost:8080/files",
        googleClientId = "g", googleClientSecret = "g",
        kakaoClientId = "k", kakaoClientSecret = "k",
        naverClientId = "n", naverClientSecret = "n",
        currentUserResolver = mock(CurrentUserResolver::class.java),
        storage = mock(StorageService::class.java),
        userRepository = userRepository,
        emailTokenRepo = mock(EmailVerificationTokenRepository::class.java),
        refreshTokenRepo = mock(RefreshTokenRepository::class.java),
        passwordEncoder = mock(PasswordEncoder::class.java),
        jwt = mock(JwtUtil::class.java),
        emailService = mock(EmailService::class.java),
        cachedUserLookupService = mock(CachedUserLookupService::class.java),
        passwordResetTokenRepo = mock(PasswordResetTokenRepository::class.java),
    )

    private val findOrCreate = UserService::class.declaredFunctions
        .first { it.name == "findOrCreateOAuthUser" }
        .apply { isAccessible = true }

    private fun link(provider: String, providerId: String, email: String, verified: Boolean?, picture: String? = null): User =
        try {
            findOrCreate.call(service, provider, providerId, email, "실명", picture, verified) as User
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

    @BeforeEach
    fun setUp() {
        `when`(userRepository.save(any(User::class.java))).thenAnswer { it.getArgument(0) }
    }

    private fun local(email: String = "star@gmail.com") =
        User(id = 7, email = email, passwordHash = "hash", name = "홍길동", phone = "010").apply { emailVerified = true }

    @Test
    fun `로컬 계정은 검증 플래그가 없거나(Naver) false 면 연동하지 않는다`() {
        val me = local()
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(me))

        assertThatThrownBy { link("naver", "n1", "star@gmail.com", null) }.isInstanceOf(ConflictException::class.java)
        assertThatThrownBy { link("kakao", "k1", "star@gmail.com", false) }.isInstanceOf(ConflictException::class.java)
        assertThat(me.provider).isNull()
        assertThat(me.providerId).isNull()
        verify(userRepository, never()).save(me)
    }

    @Test
    fun `로컬 계정은 provider 가 이메일 소유를 확인하면 같은 계정에 연동한다`() {
        val me = local()
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(me))

        val linked = link("google", "g1", "star@gmail.com", true)

        assertThat(linked).isSameAs(me)
        assertThat(me.provider).isEqualTo("google")
        assertThat(me.providerId).isEqualTo("g1")
    }

    @Test
    fun `다른 provider 로 가입된 이메일이면 409`() {
        val kakaoUser = local().apply { provider = "kakao"; providerId = "k1" }
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(kakaoUser))

        assertThatThrownBy { link("google", "g1", "star@gmail.com", true) }.isInstanceOf(ConflictException::class.java)
    }

    @Test
    fun `같은 provider 의 다른 계정이 이메일만 같으면 검증 여부와 상관없이 409 이고 providerId 를 덮어쓰지 않는다`() {
        val owner = local().apply { provider = "kakao"; providerId = "owner" }
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(owner))

        listOf(null, false, true).forEach { verified ->
            assertThatThrownBy { link("kakao", "attacker", "star@gmail.com", verified) }
                .isInstanceOf(ConflictException::class.java)
        }
        assertThat(owner.providerId).isEqualTo("owner")
    }

    @Test
    fun `providerId 로 찾은 기존 소셜 계정은 그대로 로그인된다`() {
        val owner = local().apply { provider = "naver"; providerId = "n1" }
        `when`(userRepository.findByProviderAndProviderId("naver", "n1")).thenReturn(owner)

        assertThat(link("naver", "n1", "star@gmail.com", null)).isSameAs(owner)
    }

    @Test
    fun `탈퇴한 행에는 다시 연결하지 않고 새 계정을 만든다`() {
        val withdrawn = local().apply { provider = "kakao"; providerId = "k1"; deletedAt = LocalDateTime.now() }
        `when`(userRepository.findByProviderAndProviderId("kakao", "k1")).thenReturn(withdrawn)

        val created = link("kakao", "k1", "star@gmail.com", true)

        assertThat(created).isNotSameAs(withdrawn)
        assertThat(created.id).isZero()
        assertThat(withdrawn.deletedAt).isNotNull()
    }

    @Test
    fun `이메일이 없으면 자리표시자 이메일로 만들고 미인증으로 둔다`() {
        val created = link("kakao", "123", "", null)

        assertThat(created.email).isEqualTo("kakao.123@noemail.byeoldori")
        assertThat(created.emailVerified).isFalse()
    }

    @Test
    fun `이미 프로필 사진이 있으면 소셜 사진으로 덮어쓰지 않는다`() {
        val owner = local().apply { provider = "google"; providerId = "g1"; profileImageUrl = "http://localhost:8080/files/me.png" }
        `when`(userRepository.findByProviderAndProviderId("google", "g1")).thenReturn(owner)

        link("google", "g1", "star@gmail.com", true, picture = "https://lh3.googleusercontent.com/p.jpg")

        assertThat(owner.profileImageUrl).isEqualTo("http://localhost:8080/files/me.png")
    }
}
