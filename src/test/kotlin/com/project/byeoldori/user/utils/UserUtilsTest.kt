package com.project.byeoldori.user.utils

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 사용자 도메인 소형 유틸: 전화번호 정규화, 임시 비밀번호 생성, 토큰 해시.
 */
class UserUtilsTest {

    @Nested
    inner class 전화번호정규화 {
        @Test
        fun `null·빈 문자열·공백은 빈 문자열`() {
            assertThat(PhoneNormalizer.normalize(null)).isEqualTo("")
            assertThat(PhoneNormalizer.normalize("")).isEqualTo("")
            assertThat(PhoneNormalizer.normalize("   ")).isEqualTo("")
        }

        @Test
        fun `하이픈·공백·괄호를 제거하고 숫자만 남긴다`() {
            assertThat(PhoneNormalizer.normalize("010-1234-5678")).isEqualTo("01012345678")
            assertThat(PhoneNormalizer.normalize("010 1234 5678")).isEqualTo("01012345678")
            assertThat(PhoneNormalizer.normalize("(02) 123-4567")).isEqualTo("021234567")
            assertThat(PhoneNormalizer.normalize("abc")).isEqualTo("")
        }

        @Test
        fun `국가코드 +82 는 0 으로 바꾼다`() {
            assertThat(PhoneNormalizer.normalize("+82 10-1234-5678")).isEqualTo("01012345678")
            assertThat(PhoneNormalizer.normalize("+821012345678")).isEqualTo("01012345678")
            // 서울 지역번호(02)는 국가코드를 붙여도 10자리라 변환되지 않는다 — 휴대폰(11자리 이상) 전용 규칙
            assertThat(PhoneNormalizer.normalize("82-2-123-4567")).isEqualTo("8221234567")
        }

        @Test
        fun `82 로 시작해도 11자리 미만이면 국가코드로 보지 않는다`() {
            assertThat(PhoneNormalizer.normalize("8212345678")).isEqualTo("8212345678")
            assertThat(PhoneNormalizer.normalize("82123456789")).isEqualTo("0123456789") // 11자리부터는 국가코드로 해석
        }

        @Test
        fun `같은 번호의 다른 표기는 같은 값으로 정규화된다`() {
            val forms = listOf("01012345678", "010-1234-5678", "+82 10 1234 5678", "+82 (0)10-1234-5678".replace("(0)", ""))
            assertThat(forms.map { PhoneNormalizer.normalize(it) }.toSet()).containsExactly("01012345678")
            // 국가코드 뒤에 0 을 그대로 붙여 쓴 표기(82 010 …)는 0 이 하나 더 남는다 — 정규화 대상이 아니다
            assertThat(PhoneNormalizer.normalize("82 010 1234 5678")).isEqualTo("001012345678")
        }
    }

    @Nested
    inner class 임시비밀번호 {
        @Test
        fun `기본 길이는 12`() {
            assertThat(TemporaryPassword.generate()).hasSize(12)
            assertThat(TemporaryPassword.generate(20)).hasSize(20)
            assertThat(TemporaryPassword.generate(8)).hasSize(8)
        }

        @Test
        fun `8자 미만은 거부`() {
            assertThatThrownBy { TemporaryPassword.generate(7) }.isInstanceOf(IllegalArgumentException::class.java)
            assertThatThrownBy { TemporaryPassword.generate(0) }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `소문자·대문자·숫자·특수문자를 각각 1개 이상 포함하고 비밀번호 규칙을 통과한다`() {
            repeat(200) {
                val pw = TemporaryPassword.generate()
                assertThat(pw).`as`(pw).matches(".*[a-z].*")
                assertThat(pw).`as`(pw).matches(".*[A-Z].*")
                assertThat(pw).`as`(pw).matches(".*[0-9].*")
                assertThat(pw).`as`(pw).matches(".*[!@#\$%^&*()\\-_=+].*")
                assertThat(PasswordValidator.isValid(pw)).`as`(pw).isTrue()
            }
        }

        @Test
        fun `헷갈리는 글자(i l o I L O 0 1)는 쓰지 않는다`() {
            repeat(200) {
                val pw = TemporaryPassword.generate(32)
                assertThat(pw).`as`(pw).doesNotContainPattern("[iloILO01]")
            }
        }

        @Test
        fun `연속 생성 결과는 서로 다르다`() {
            val set = (1..50).map { TemporaryPassword.generate() }.toSet()
            assertThat(set).hasSize(50)
        }
    }

    @Nested
    inner class 토큰해시 {
        @Test
        fun `SHA-256 소문자 16진수 64자`() {
            assertThat(TokenHasher.sha256Hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
            assertThat(TokenHasher.sha256Hex("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        }

        @Test
        fun `같은 입력은 같은 해시, 다른 입력은 다른 해시`() {
            assertThat(TokenHasher.sha256Hex("token-1")).isEqualTo(TokenHasher.sha256Hex("token-1"))
            assertThat(TokenHasher.sha256Hex("token-1")).isNotEqualTo(TokenHasher.sha256Hex("token-2"))
        }

        @Test
        fun `한글 입력도 64자 16진수`() {
            assertThat(TokenHasher.sha256Hex("별도리")).hasSize(64).matches("[0-9a-f]{64}")
        }
    }
}
