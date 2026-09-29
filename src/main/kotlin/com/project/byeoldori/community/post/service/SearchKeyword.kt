package com.project.byeoldori.community.post.service

/**
 * 커뮤니티 제목·본문 검색어 정리.
 *
 * 검색은 MySQL FULLTEXT(ngram, 토큰 2글자) + BOOLEAN MODE 다. 두 가지를 보정한다.
 * 1) 사용자가 친 + - < > ( ) ~ * " @ 는 BOOLEAN MODE 연산자라 "-오리온"이 '오리온 제외'가 되는 식으로
 *    의도와 다르게 동작한다 → 공백으로 바꿔 글자 그대로 검색되게 한다.
 * 2) ngram 토큰이 2글자라 "별"·"달" 같은 한 글자 검색어는 FULLTEXT 로 절대 못 찾는다
 *    → 모든 단어가 한 글자면 LIKE 로 대체한다(운영 VM 격리 DB 실측으로 확인, 2026-09-30).
 */
object SearchKeyword {
    private val BOOLEAN_OPERATORS = Regex("[+\\-<>()~*\"@]")
    private const val NGRAM_TOKEN_SIZE = 2

    sealed interface Plan
    /** FULLTEXT BOOLEAN MODE 에 넘길 식(마지막 단어 앞부분 일치 `*` 포함) */
    data class FullText(val against: String) : Plan
    /** LIKE '%...%' 에 넘길 값. %, _, ! 는 '!' 로 이스케이프(JPQL escape '!').
     *  역슬래시는 MySQL 문자열 리터럴의 특수문자라 escape 문자로 쓰면 방언에 따라 문법 오류가 날 수 있다. */
    data class Like(val pattern: String) : Plan
    /** 정리하고 나니 검색할 글자가 없음 → 빈 결과 */
    data object Empty : Plan

    fun plan(raw: String): Plan {
        val cleaned = raw.replace(BOOLEAN_OPERATORS, " ").trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return Empty
        val terms = cleaned.split(' ')
        return if (terms.all { it.length < NGRAM_TOKEN_SIZE }) {
            Like(escapeLike(cleaned))
        } else {
            FullText("$cleaned*")
        }
    }

    private fun escapeLike(s: String): String =
        s.replace("!", "!!").replace("%", "!%").replace("_", "!_")
}
