package com.project.byeoldori.education.program.migration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 예전 JSON 프로그램 URL → 업로드 폴더 상대경로 변환 (순수 함수). */
class LegacyJsonPathTest {

    @Test
    fun `호스트와 무관하게 json 경로를 뽑는다`() {
        listOf(
            "https://byeoldori.duckdns.org/json/2026/07/10/abc.json",
            "https://api.byeoldori.com/json/2026/07/10/abc.json",
            "https://byeoldori-server-hbxnfn4woa-du.a.run.app/json/2026/07/10/abc.json",
        ).forEach {
            assertThat(legacyJsonRelativePath(it)).`as`(it).isEqualTo("json/2026/07/10/abc.json")
        }
    }

    @Test
    fun `files 서빙 경로 형식도 처리한다`() {
        assertThat(legacyJsonRelativePath("https://api.byeoldori.com/files/json/2026/07/10/abc.json"))
            .isEqualTo("json/2026/07/10/abc.json")
    }

    @Test
    fun `json 이 아니거나 형식이 다르면 null`() {
        assertThat(legacyJsonRelativePath("https://api.byeoldori.com/files/2026/07/10/a.png")).isNull()
        assertThat(legacyJsonRelativePath("https://example.com/other/a.json")).isNull()
        assertThat(legacyJsonRelativePath("not a url with spaces")).isNull()
    }
}
