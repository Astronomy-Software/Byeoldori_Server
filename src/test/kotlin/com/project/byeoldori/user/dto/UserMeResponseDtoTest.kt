package com.project.byeoldori.user.dto

import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 내 정보 응답 매핑. 온보딩 필요 여부(닉네임·전화번호 누락)와 역할 집합 복사를 확인한다.
 */
class UserMeResponseDtoTest {

    private fun user(nickname: String? = "별지기", phone: String = "01012345678") = User(
        id = 42, email = "me@test.com", passwordHash = "hash", name = "서범수", phone = phone, nickname = nickname,
        birthdate = LocalDate.of(1999, 7, 10), emailVerified = true, lastLoginAt = LocalDateTime.of(2026, 10, 20, 9, 0),
        profileImageUrl = "https://img/x.png", roles = mutableSetOf("USER", "ADMIN"),
    )

    @Test
    fun `모든 필드가 그대로 옮겨지고 비밀번호 해시는 포함되지 않는다`() {
        val u = user()
        val dto = UserMeResponseDto.from(u)

        assertThat(dto.id).isEqualTo(42)
        assertThat(dto.email).isEqualTo("me@test.com")
        assertThat(dto.name).isEqualTo("서범수")
        assertThat(dto.phone).isEqualTo("01012345678")
        assertThat(dto.nickname).isEqualTo("별지기")
        // 생년월일은 수집 중단 — DB 에 예전 값이 남아 있어도 응답에는 내보내지 않는다
        assertThat(dto.birthdate).isNull()
        assertThat(dto.emailVerified).isTrue()
        assertThat(dto.lastLoginAt).isEqualTo(LocalDateTime.of(2026, 10, 20, 9, 0))
        assertThat(dto.roles).containsExactlyInAnyOrder("USER", "ADMIN")
        assertThat(dto.profileImageUrl).isEqualTo("https://img/x.png")
        assertThat(dto.createdAt).isEqualTo(u.createdAt)
        assertThat(dto.updatedAt).isEqualTo(u.updatedAt)
        assertThat(UserMeResponseDto::class.java.declaredFields.map { it.name }).doesNotContain("passwordHash")
    }

    @Test
    fun `닉네임과 전화번호가 모두 있으면 온보딩 불필요`() {
        assertThat(UserMeResponseDto.from(user()).onboardingRequired).isFalse()
    }

    @Test
    fun `닉네임이 없거나 공백이면 온보딩 필요`() {
        assertThat(UserMeResponseDto.from(user(nickname = null)).onboardingRequired).isTrue()
        assertThat(UserMeResponseDto.from(user(nickname = "")).onboardingRequired).isTrue()
        assertThat(UserMeResponseDto.from(user(nickname = "   ")).onboardingRequired).isTrue()
    }

    @Test
    fun `전화번호가 비어 있으면 온보딩 필요(소셜 가입 직후)`() {
        assertThat(UserMeResponseDto.from(user(phone = "")).onboardingRequired).isTrue()
        assertThat(UserMeResponseDto.from(user(phone = "  ")).onboardingRequired).isTrue()
    }

    @Test
    fun `역할 집합은 복사본이라 원본 변경에 영향받지 않는다`() {
        val u = user()
        val dto = UserMeResponseDto.from(u)
        u.roles.add("MODERATOR")
        assertThat(dto.roles).containsExactlyInAnyOrder("USER", "ADMIN")
    }
}
