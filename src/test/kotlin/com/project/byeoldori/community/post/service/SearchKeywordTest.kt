package com.project.byeoldori.community.post.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 검색어 → FULLTEXT / LIKE / 빈 결과 선택 규칙 (순수 함수). */
class SearchKeywordTest {

    @Test
    fun `두 글자 이상은 FULLTEXT 앞부분 일치로 검색한다`() {
        assertThat(SearchKeyword.plan("오리온")).isEqualTo(SearchKeyword.FullText("오리온*"))
        assertThat(SearchKeyword.plan("  관측   후기 ")).isEqualTo(SearchKeyword.FullText("관측 후기*"))
    }

    @Test
    fun `모든 단어가 한 글자면 LIKE 로 대체한다(ngram 토큰 2글자 한계)`() {
        assertThat(SearchKeyword.plan("별")).isEqualTo(SearchKeyword.Like("별"))
        assertThat(SearchKeyword.plan("별 달")).isEqualTo(SearchKeyword.Like("별 달"))
    }

    @Test
    fun `한 단어라도 두 글자 이상이면 FULLTEXT`() {
        assertThat(SearchKeyword.plan("별 관측")).isEqualTo(SearchKeyword.FullText("별 관측*"))
    }

    @Test
    fun `BOOLEAN MODE 연산자는 글자 그대로 검색되도록 지운다`() {
        assertThat(SearchKeyword.plan("-오리온")).isEqualTo(SearchKeyword.FullText("오리온*"))
        assertThat(SearchKeyword.plan("(오리온)")).isEqualTo(SearchKeyword.FullText("오리온*"))
        assertThat(SearchKeyword.plan("\"북두칠성\"")).isEqualTo(SearchKeyword.FullText("북두칠성*"))
        assertThat(SearchKeyword.plan("+별")).isEqualTo(SearchKeyword.Like("별"))
    }

    @Test
    fun `연산자만 있으면 빈 결과`() {
        assertThat(SearchKeyword.plan("+-*()\"@~<>")).isEqualTo(SearchKeyword.Empty)
        assertThat(SearchKeyword.plan("   ")).isEqualTo(SearchKeyword.Empty)
    }

    @Test
    fun `LIKE 와일드카드 문자는 이스케이프한다`() {
        assertThat(SearchKeyword.plan("%")).isEqualTo(SearchKeyword.Like("!%"))
        assertThat(SearchKeyword.plan("_")).isEqualTo(SearchKeyword.Like("!_"))
        assertThat(SearchKeyword.plan("!")).isEqualTo(SearchKeyword.Like("!!"))
    }
}
