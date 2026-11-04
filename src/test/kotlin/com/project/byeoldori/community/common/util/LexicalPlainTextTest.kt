package com.project.byeoldori.community.common.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 자유게시판 목록 요약은 Lexical JSON 의 텍스트 노드만 모은 평문이어야 한다
 * (예전엔 JSON 앞 30자라 웹이 모든 글을 "리치 텍스트 게시글"로 표시했다).
 */
class LexicalPlainTextTest {

    private fun text(t: String, format: Int = 0) =
        """{"detail":0,"format":$format,"mode":"normal","style":"","text":"$t","type":"text","version":1}"""

    private fun paragraph(vararg children: String) =
        """{"children":[${children.joinToString(",")}],"direction":"ltr","format":"","indent":0,"type":"paragraph","version":1}"""

    private fun root(vararg children: String) =
        """{"root":{"children":[${children.joinToString(",")}],"direction":"ltr","format":"","indent":0,"type":"root","version":1}}"""

    @Test
    fun `텍스트 노드를 이어 붙이고 문단 사이는 공백 하나로 잇는다`() {
        val json = root(
            paragraph(text("오늘 "), text("토성", format = 1), text("을 봤다")),
            paragraph(text("고리가 선명했다")),
        )
        assertThat(LexicalPlainText.summarize(json, 30)).isEqualTo("오늘 토성을 봤다 고리가 선명했다")
        assertThat(LexicalPlainText.summarize(json, 5)).isEqualTo("오늘 토성")
    }

    @Test
    fun `평문과 깨진 JSON 은 원문 앞부분을 쓴다`() {
        assertThat(LexicalPlainText.summarize("그냥 평문 본문입니다", 4)).isEqualTo("그냥 평")
        val broken = """{"root":{"children":["""
        assertThat(LexicalPlainText.summarize(broken, 30)).isEqualTo(broken)
        assertThat(LexicalPlainText.summarize("""{"a":1}""", 30)).isEqualTo("""{"a":1}""")
    }

    @Test
    fun `글자가 없는 본문(이미지만 등)은 빈 요약`() {
        assertThat(LexicalPlainText.summarize(root(paragraph()), 30)).isEmpty()
    }

    @Test
    fun `아주 긴 본문도 앞부분만 읽어 요약한다`() {
        val many = (1..5000).map { paragraph(text("문단$it")) }.toTypedArray()
        assertThat(LexicalPlainText.summarize(root(*many), 10)).isEqualTo("문단1 문단2 문단")
    }
}
