package com.project.byeoldori.user.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserRulesTest {

    @Test
    fun `이메일 마스킹 - 앞 두 글자와 최상위 도메인만 남긴다`() {
        assertThat(EmailMasker.mask("beom710@gmail.com")).isEqualTo("be***@gm***.com")
        assertThat(EmailMasker.mask("ab@naver.com")).isEqualTo("a***@na***.com")
        assertThat(EmailMasker.mask("a@b.io")).isEqualTo("a***@b***.io")
        assertThat(EmailMasker.mask("kakao.123@noemail.byeoldori")).isEqualTo("ka***@no***.byeoldori")
        assertThat(EmailMasker.mask("user@localhost")).isEqualTo("us***@lo***")
    }

    @Test
    fun `이메일 마스킹 - 형식이 깨진 값은 통째로 가린다`() {
        assertThat(EmailMasker.mask("noatsign")).isEqualTo("***")
        assertThat(EmailMasker.mask("@gmail.com")).isEqualTo("***")
        assertThat(EmailMasker.mask("user@")).isEqualTo("***")
    }

    @Test
    fun `닉네임 정규화 - 앞뒤 공백 제거, 비면 null`() {
        assertThat(NicknameRules.normalize("  별지기  ")).isEqualTo("별지기")
        assertThat(NicknameRules.normalize("")).isNull()
        assertThat(NicknameRules.normalize("   ")).isNull()
        assertThat(NicknameRules.normalize(null)).isNull()
    }

    @Test
    fun `탈퇴 계정 이메일 자리표시자는 id 별로 고유하고 내부 도메인을 쓴다`() {
        assertThat(DeletedAccount.tombstoneEmail(42)).isEqualTo("deleted+42@deleted.byeoldori")
        assertThat(DeletedAccount.tombstoneEmail(1)).isNotEqualTo(DeletedAccount.tombstoneEmail(2))
    }
}
