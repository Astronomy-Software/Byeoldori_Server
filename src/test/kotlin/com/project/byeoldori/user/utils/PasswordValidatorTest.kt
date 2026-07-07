package com.project.byeoldori.user.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * PasswordValidator 규칙 검증 (순수 함수, Spring 컨텍스트 불필요).
 * 규칙: 길이 8..64, 영문(대/소) 1+, 숫자 1+, 특수문자 [!@#$%^&*()_=+-] 1+
 */
class PasswordValidatorTest {

    @Test
    fun `유효한 비밀번호는 통과한다`() {
        val valid = listOf(
            "Password1!",
            "abcd1234!",
            "aB3=xyzqq",
            "A1!aaaaa",          // 정확히 8자 경계
            "letters123&more"
        )
        valid.forEach { pw ->
            assertThat(PasswordValidator.isValid(pw))
                .`as`("유효해야 함: %s", pw)
                .isTrue()
        }
    }

    @Test
    fun `특수문자 집합의 각 문자는 모두 허용된다`() {
        // 정규식 문자 클래스: ! @ # $ % ^ & * ( ) _ = + -
        val specials = listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '_', '=', '+', '-')
        specials.forEach { c ->
            val pw = "Abcd123$c"   // 영문+숫자+해당 특수문자, 길이 8
            assertThat(PasswordValidator.isValid(pw))
                .`as`("특수문자 '%s' 허용되어야 함", c)
                .isTrue()
        }
    }

    @Test
    fun `특수문자가 없으면 실패한다`() {
        assertThat(PasswordValidator.isValid("Password1")).isFalse()
    }

    @Test
    fun `숫자가 없으면 실패한다`() {
        assertThat(PasswordValidator.isValid("Password!")).isFalse()
    }

    @Test
    fun `영문이 없으면 실패한다`() {
        assertThat(PasswordValidator.isValid("12345678!")).isFalse()
    }

    @Test
    fun `허용되지 않은 특수문자만 있으면 실패한다`() {
        // '?' 와 '.' 는 허용 집합에 없음 → 특수문자 조건 미충족
        assertThat(PasswordValidator.isValid("Password1?")).isFalse()
        assertThat(PasswordValidator.isValid("Password1.")).isFalse()
    }

    @Test
    fun `8자 미만이면 실패한다`() {
        assertThat(PasswordValidator.isValid("Ab1!")).isFalse()
        assertThat(PasswordValidator.isValid("Abcd12!")).isFalse() // 7자
    }

    @Test
    fun `64자를 초과하면 실패한다`() {
        val over = "A" + "1" + "!" + "a".repeat(62) // 총 65자, 규칙 충족하나 길이 초과
        assertThat(over.length).isEqualTo(65)
        assertThat(PasswordValidator.isValid(over)).isFalse()
    }

    @Test
    fun `정확히 64자는 통과한다`() {
        val exact = "A" + "1" + "!" + "a".repeat(61) // 총 64자
        assertThat(exact.length).isEqualTo(64)
        assertThat(PasswordValidator.isValid(exact)).isTrue()
    }

    @Test
    fun `빈 문자열은 실패한다`() {
        assertThat(PasswordValidator.isValid("")).isFalse()
    }
}
