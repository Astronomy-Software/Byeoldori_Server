package com.project.byeoldori.service

import com.project.byeoldori.common.exception.ConflictException
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.security.CurrentUserResolver
import com.project.byeoldori.security.JwtUtil
import com.project.byeoldori.user.dto.LoginRequestDto
import com.project.byeoldori.user.dto.SignupRequestDto
import com.project.byeoldori.user.entity.EmailVerificationToken
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.EmailVerificationTokenRepository
import com.project.byeoldori.user.repository.PasswordResetTokenRepository
import com.project.byeoldori.user.repository.RefreshTokenRepository
import com.project.byeoldori.user.repository.UserRepository
import com.project.byeoldori.user.service.CachedUserLookupService
import com.project.byeoldori.user.service.EmailService
import com.project.byeoldori.user.service.UserService
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class UserServiceTest {

    @Mock lateinit var currentUserResolver: CurrentUserResolver
    @Mock lateinit var storage: StorageService
    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var emailTokenRepo: EmailVerificationTokenRepository
    @Mock lateinit var refreshTokenRepo: RefreshTokenRepository
    @Mock lateinit var passwordEncoder: PasswordEncoder
    @Mock lateinit var jwt: JwtUtil
    @Mock lateinit var emailService: EmailService
    @Mock lateinit var googleVerifier: GoogleIdTokenVerifier
    @Mock lateinit var cachedUserLookupService: CachedUserLookupService
    @Mock lateinit var passwordResetTokenRepo: PasswordResetTokenRepository

    lateinit var userService: UserService

    @BeforeEach
    fun setup() {
        userService = UserService(
            publicBaseUrl = "https://test.example.com",
            currentUserResolver = currentUserResolver,
            storage = storage,
            userRepository = userRepository,
            emailTokenRepo = emailTokenRepo,
            refreshTokenRepo = refreshTokenRepo,
            passwordEncoder = passwordEncoder,
            jwt = jwt,
            emailService = emailService,
            googleVerifier = googleVerifier,
            cachedUserLookupService = cachedUserLookupService,
            passwordResetTokenRepo = passwordResetTokenRepo
        )
    }

    // ───────────── signup ─────────────

    @Test
    fun `회원가입 성공`() {
        val req = SignupRequestDto(
            email = "test@example.com",
            password = "Abcd1234!",
            passwordConfirm = "Abcd1234!",
            name = "홍길동",
            phone = "01012345678",
            nickname = "tester",
            birthdate = null
        )
        given(userRepository.existsByEmail(req.email)).willReturn(false)
        given(userRepository.existsByNickname("tester")).willReturn(false)
        given(passwordEncoder.encode(anyString())).willReturn("hashed")
        val savedUser = User(email = req.email, passwordHash = "hashed", name = req.name, phone = req.phone, nickname = req.nickname)
        given(userRepository.save(any(User::class.java))).willReturn(savedUser)
        given(emailTokenRepo.save(any(EmailVerificationToken::class.java))).willReturn(EmailVerificationToken(user = savedUser))

        assertDoesNotThrow { userService.signup(req) }
        verify(emailService).sendEmailVerification(eq(req.email), any())
    }

    @Test
    fun `이메일 중복 시 ConflictException`() {
        val req = SignupRequestDto(
            email = "dup@example.com",
            password = "Abcd1234!",
            passwordConfirm = "Abcd1234!",
            name = "홍길동",
            phone = "01012345678"
        )
        given(userRepository.existsByEmail(req.email)).willReturn(true)

        assertThrows<ConflictException> { userService.signup(req) }
    }

    @Test
    fun `비밀번호 불일치 시 InvalidInputException`() {
        val req = SignupRequestDto(
            email = "test@example.com",
            password = "Abcd1234!",
            passwordConfirm = "Different1!",
            name = "홍길동",
            phone = "01012345678"
        )

        assertThrows<InvalidInputException> { userService.signup(req) }
    }

    // ───────────── login ─────────────

    @Test
    fun `로그인 성공 시 토큰 반환`() {
        val req = LoginRequestDto(email = "test@example.com", password = "Abcd1234!")
        val user = User(id = 1L, email = req.email, passwordHash = "hashed", name = "홍길동", phone = "010", emailVerified = true)

        given(userRepository.findByEmail(req.email)).willReturn(Optional.of(user))
        given(passwordEncoder.matches(req.password, "hashed")).willReturn(true)
        given(jwt.generateAccessToken(req.email)).willReturn("access-token")
        given(jwt.generateRefreshToken(req.email)).willReturn("refresh-token")
        given(jwt.extractExpiration(anyString())).willReturn(java.time.LocalDateTime.now().plusHours(1))
        given(refreshTokenRepo.findByUserIdForUpdate(user.id)).willReturn(Optional.empty())

        val result = userService.login(req)

        assertNotNull(result.accessToken)
    }

    @Test
    fun `존재하지 않는 이메일 로그인 시 NotFoundException`() {
        val req = LoginRequestDto(email = "none@example.com", password = "Abcd1234!")
        given(userRepository.findByEmail(req.email)).willReturn(Optional.empty())

        assertThrows<NotFoundException> { userService.login(req) }
    }

    @Test
    fun `이메일 미인증 상태 로그인 시 ForbiddenException`() {
        val req = LoginRequestDto(email = "test@example.com", password = "Abcd1234!")
        val user = User(id = 1L, email = req.email, passwordHash = "hashed", name = "홍길동", phone = "010", emailVerified = false)

        given(userRepository.findByEmail(req.email)).willReturn(Optional.of(user))

        assertThrows<ForbiddenException> { userService.login(req) }
    }

    @Test
    fun `비밀번호 불일치 로그인 시 InvalidInputException`() {
        val req = LoginRequestDto(email = "test@example.com", password = "WrongPass1!")
        val user = User(id = 1L, email = req.email, passwordHash = "hashed", name = "홍길동", phone = "010", emailVerified = true)

        given(userRepository.findByEmail(req.email)).willReturn(Optional.of(user))
        given(passwordEncoder.matches(req.password, "hashed")).willReturn(false)

        assertThrows<InvalidInputException> { userService.login(req) }
    }

    // ───────────── findEmailsByNameAndPhone ─────────────

    @Test
    fun `이름과 전화번호로 이메일 목록 반환`() {
        val users = listOf(
            User(email = "a@test.com", passwordHash = "", name = "홍길동", phone = "01012345678"),
            User(email = "b@test.com", passwordHash = "", name = "홍길동", phone = "01012345678")
        )
        given(userRepository.findAllByNameAndPhone("홍길동", "01012345678")).willReturn(users)

        val result = userService.findEmailsByNameAndPhone("홍길동", "01012345678")

        assertEquals(listOf("a@test.com", "b@test.com"), result)
    }
}
