package com.project.byeoldori.security

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.common.exception.UnauthorizedException
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.LocalDateTime
import java.util.Optional

/**
 * 현재 사용자 해석(프로덕션 보안 코드는 건드리지 않고 동작만 고정한다).
 * 요청 속성 → SecurityContext principal 타입별(User / UserDetails / String / Number) 순서와 탈퇴 계정 차단을 확인한다.
 */
class CurrentUserResolverTest {

    private val repo = mock(UserRepository::class.java)
    private val resolver = CurrentUserResolver(repo)

    private val alice = User(id = 1, email = "alice@test.com", passwordHash = "x", name = "앨리스", phone = "010")
    private val deleted = User(id = 2, email = "deleted+2@deleted.byeoldori", passwordHash = "x", name = "탈퇴", phone = "", deletedAt = LocalDateTime.now())

    @BeforeEach
    fun setUp() {
        `when`(repo.findById(1L)).thenReturn(Optional.of(alice))
        `when`(repo.findById(2L)).thenReturn(Optional.of(deleted))
        `when`(repo.findByEmail("alice@test.com")).thenReturn(Optional.of(alice))
        `when`(repo.findByEmail("deleted+2@deleted.byeoldori")).thenReturn(Optional.of(deleted))
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
    }

    private fun authenticate(principal: Any) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    @Test
    fun `인증 정보가 없으면 LOGIN_REQUIRED 로 Unauthorized`() {
        assertThatThrownBy { resolver.getUser() }
            .isInstanceOf(UnauthorizedException::class.java)
            .hasMessage(ErrorCode.LOGIN_REQUIRED.message)
    }

    @Test
    fun `principal 이 User 엔티티면 id 로 다시 조회한다(세션의 stale 객체를 쓰지 않는다)`() {
        val stale = User(id = 1, email = "old@test.com", passwordHash = "x", name = "옛값", phone = "010")
        authenticate(stale)

        val user = resolver.getUser()

        assertThat(user).isSameAs(alice)
        verify(repo).findById(1L)
    }

    @Test
    fun `principal 이 UserDetails 면 username 을 이메일로 조회`() {
        authenticate(SpringUser.withUsername("alice@test.com").password("x").roles("USER").build())
        assertThat(resolver.getUser()).isSameAs(alice)
        verify(repo).findByEmail("alice@test.com")
        verify(repo, never()).findById(1L)
    }

    @Test
    fun `principal 이 문자열이면 이메일로 조회`() {
        authenticate("alice@test.com")
        assertThat(resolver.getUser()).isSameAs(alice)
    }

    @Test
    fun `principal 이 숫자면 id 로 조회(Int·Long 모두)`() {
        authenticate(1)
        assertThat(resolver.getUser()).isSameAs(alice)
        authenticate(1L)
        assertThat(resolver.getUser()).isSameAs(alice)
    }

    @Test
    fun `알 수 없는 principal 타입은 Unauthorized`() {
        authenticate(Any())
        assertThatThrownBy { resolver.getUser() }
            .isInstanceOf(UnauthorizedException::class.java)
            .hasMessageContaining("잘못된 인증 컨텍스트")
    }

    @Test
    fun `DB 에 없는 사용자는 USER_NOT_FOUND`() {
        `when`(repo.findById(99L)).thenReturn(Optional.empty())
        `when`(repo.findByEmail("ghost@test.com")).thenReturn(Optional.empty())

        authenticate(99L)
        assertThatThrownBy { resolver.getUser() }
            .isInstanceOf(NotFoundException::class.java)
            .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND)

        authenticate("ghost@test.com")
        assertThatThrownBy { resolver.getUser() }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `탈퇴한 계정은 어떤 경로로도 Unauthorized`() {
        authenticate(2L)
        assertThatThrownBy { resolver.getUser() }.isInstanceOf(UnauthorizedException::class.java).hasMessageContaining("탈퇴")

        authenticate("deleted+2@deleted.byeoldori")
        assertThatThrownBy { resolver.getUser() }.isInstanceOf(UnauthorizedException::class.java).hasMessageContaining("탈퇴")

        authenticate(deleted)
        assertThatThrownBy { resolver.getUser() }.isInstanceOf(UnauthorizedException::class.java).hasMessageContaining("탈퇴")
    }

    @Test
    fun `요청 속성 currentUser 가 있으면 SecurityContext 보다 우선한다`() {
        val req = MockHttpServletRequest()
        req.setAttribute("currentUser", User(id = 1, email = "attr@test.com", passwordHash = "x", name = "a", phone = "010"))
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        // SecurityContext 는 비어 있어도 된다
        assertThat(resolver.getUser()).isSameAs(alice)
    }

    @Test
    fun `요청 속성의 사용자가 탈퇴 상태면 Unauthorized`() {
        val req = MockHttpServletRequest()
        req.setAttribute("currentUser", User(id = 2, email = "d", passwordHash = "x", name = "d", phone = ""))
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        assertThatThrownBy { resolver.getUser() }.isInstanceOf(UnauthorizedException::class.java).hasMessageContaining("탈퇴")
    }

    @Test
    fun `요청 속성이 User 타입이 아니면 무시하고 SecurityContext 로 간다`() {
        val req = MockHttpServletRequest()
        req.setAttribute("currentUser", "not-a-user")
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(req))
        authenticate("alice@test.com")
        assertThat(resolver.getUser()).isSameAs(alice)
    }
}
