package com.project.byeoldori.user.service

import com.project.byeoldori.common.exception.ConflictException
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.LoginRequestDto
import com.project.byeoldori.user.dto.SignupRequestDto
import com.project.byeoldori.user.dto.UserUpdateRequestDto
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.EmailVerificationTokenRepository
import com.project.byeoldori.user.repository.PasswordResetTokenRepository
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime
import java.util.Optional

/**
 * 회원 관련 수정 검증(스프링 컨텍스트 없이 mock):
 * 닉네임 빈 값 저장, 탈퇴 계정의 이메일 점유, 아이디 찾기 이메일 원문 노출, 로그인 응답 차이로 인한 계정 열거.
 */
class UserServiceAccountTest {

    private val currentUserResolver = mock(CurrentUserResolver::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val cachedUserLookupService = mock(CachedUserLookupService::class.java)

    private val service = UserService(
        publicBaseUrl = "http://localhost:8080/files",
        googleClientId = "g", googleClientSecret = "g",
        kakaoClientId = "k", kakaoClientSecret = "k",
        naverClientId = "n", naverClientSecret = "n",
        currentUserResolver = currentUserResolver,
        storage = mock(StorageService::class.java),
        userRepository = userRepository,
        emailTokenRepo = mock(EmailVerificationTokenRepository::class.java),
        refreshTokenRepo = mock(RefreshTokenRepository::class.java),
        passwordEncoder = passwordEncoder,
        jwt = mock(JwtUtil::class.java),
        emailService = mock(EmailService::class.java),
        cachedUserLookupService = cachedUserLookupService,
        passwordResetTokenRepo = mock(PasswordResetTokenRepository::class.java),
        oauthWebClient = mock(WebClient::class.java),
    )

    private fun user(id: Long = 7, email: String = "star@gmail.com", nickname: String? = "별지기") =
        User(id = id, email = email, passwordHash = "hash", name = "홍길동", phone = "01012345678", nickname = nickname)

    // ── 닉네임 ──

    @Test
    fun `닉네임을 빈 값으로 바꾸면 저장하지 않고 400`() {
        val me = user()
        `when`(currentUserResolver.getUser()).thenReturn(me)

        assertThatThrownBy { service.updateMe(UserUpdateRequestDto(nickname = "   ")) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThat(me.nickname).isEqualTo("별지기")
    }

    @Test
    fun `닉네임은 앞뒤 공백을 지워 저장하고 20자를 넘으면 400`() {
        val me = user()
        `when`(currentUserResolver.getUser()).thenReturn(me)

        service.updateMe(UserUpdateRequestDto(nickname = "  새별  "))
        assertThat(me.nickname).isEqualTo("새별")

        assertThatThrownBy { service.updateMe(UserUpdateRequestDto(nickname = "가".repeat(21))) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThat(me.nickname).isEqualTo("새별")
    }

    @Test
    fun `같은 닉네임을 다시 보내면 중복 검사 없이 통과(기존 긴 닉네임 사용자도 다른 항목 수정 가능)`() {
        val long = "가".repeat(25)
        val me = user(nickname = long)
        `when`(currentUserResolver.getUser()).thenReturn(me)

        service.updateMe(UserUpdateRequestDto(nickname = long, phone = "01099998888"))
        assertThat(me.phone).isEqualTo("01099998888")
        verify(userRepository, never()).existsByNickname(long)
    }

    @Test
    fun `이미 쓰는 닉네임이면 409`() {
        `when`(currentUserResolver.getUser()).thenReturn(user())
        `when`(userRepository.existsByNickname("남의별")).thenReturn(true)

        assertThatThrownBy { service.updateMe(UserUpdateRequestDto(nickname = "남의별")) }
            .isInstanceOf(ConflictException::class.java)
    }

    // ── 탈퇴 ──

    @Test
    fun `탈퇴하면 이메일·실명을 자리표시자로 바꿔 원래 이메일이 풀린다`() {
        val me = user(id = 42)
        `when`(currentUserResolver.getUser()).thenReturn(me)

        service.deleteAccount()

        assertThat(me.email).isEqualTo("deleted+42@deleted.byeoldori")
        assertThat(me.name).isEqualTo("탈퇴한 사용자")
        assertThat(me.deletedAt).isNotNull()
        verify(cachedUserLookupService).evictByEmail("star@gmail.com")
    }

    @Test
    fun `내부 전용 도메인 이메일로는 가입할 수 없다`() {
        listOf("deleted+1@deleted.byeoldori", "kakao.1@NOEMAIL.byeoldori").forEach { email ->
            val req = SignupRequestDto(
                email = email, password = "Passw0rd!", passwordConfirm = "Passw0rd!",
                name = "n", phone = "010", nickname = null
            )
            assertThatThrownBy { service.signup(req) }.isInstanceOf(InvalidInputException::class.java)
        }
    }

    // ── 아이디 찾기 ──

    @Test
    fun `아이디 찾기는 마스킹한 이메일만 돌려주고 탈퇴 계정은 뺀다`() {
        val active = user(id = 1, email = "beom710@gmail.com")
        val deleted = user(id = 2, email = "old@naver.com").apply { deletedAt = LocalDateTime.now() }
        `when`(userRepository.findAllByNameAndPhone("홍길동", "01012345678")).thenReturn(listOf(active, deleted))

        assertThat(service.findEmailsByNameAndPhone("홍길동", "01012345678")).containsExactly("be***@gm***.com")
    }

    // ── 로그인 ──

    @Test
    fun `미가입 이메일과 틀린 비밀번호는 같은 예외·메시지`() {
        `when`(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty())
        val exists = user(email = "star@gmail.com")
        `when`(userRepository.findByEmail("star@gmail.com")).thenReturn(Optional.of(exists))
        `when`(passwordEncoder.matches("wrong", "hash")).thenReturn(false)

        val notFound = runCatching { service.login(LoginRequestDto("none@test.com", "wrong")) }.exceptionOrNull()
        val wrongPw = runCatching { service.login(LoginRequestDto("star@gmail.com", "wrong")) }.exceptionOrNull()

        assertThat(notFound).isInstanceOf(InvalidInputException::class.java)
        assertThat(wrongPw).isInstanceOf(InvalidInputException::class.java)
        assertThat(notFound!!.message).isEqualTo(wrongPw!!.message)
    }

    @Test
    fun `이메일 미인증 안내는 비밀번호가 맞을 때만 나온다`() {
        val unverified = user(email = "new@gmail.com").apply { emailVerified = false }
        `when`(userRepository.findByEmail("new@gmail.com")).thenReturn(Optional.of(unverified))
        `when`(passwordEncoder.matches("wrong", "hash")).thenReturn(false)
        `when`(passwordEncoder.matches("right", "hash")).thenReturn(true)

        assertThatThrownBy { service.login(LoginRequestDto("new@gmail.com", "wrong")) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { service.login(LoginRequestDto("new@gmail.com", "right")) }
            .isInstanceOf(ForbiddenException::class.java)
    }
}
