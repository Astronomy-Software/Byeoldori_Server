package com.project.byeoldori.community.common.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken

/**
 * 웹 에디터가 저장한 본문(Lexical editorState JSON 문자열)에서 평문 요약을 만든다.
 *
 * 예전엔 본문 앞 30자를 그대로 잘라 요약이 항상 `{"root":{"children":...` 같은 JSON 조각이었고,
 * 웹은 이를 감지해 모든 글을 "리치 텍스트 게시글"로 표시했다.
 * 목록 조회마다 호출되므로 트리 전체를 읽지 않고 스트리밍으로 필요한 글자 수만큼만 읽는다.
 */
object LexicalPlainText {
    private val factory = JsonFactory()
    private val WHITESPACE = Regex("\\s+")

    // 자식 뒤에 공백을 넣지 않는 인라인 노드(글자 사이에 끼어든다). 그 밖의 노드(문단·제목·줄바꿈 등)는 끝에 공백.
    private val inlineTypes = setOf("text", "link", "autolink", "hashtag", "code-highlight")

    /** Lexical JSON 이면 텍스트 노드를 이어 붙인 평문 앞 [limit]자, 아니면(평문·깨진 JSON) 원문 앞 [limit]자. */
    fun summarize(content: String, limit: Int): String {
        if (!content.trimStart().startsWith("{")) return content.take(limit)
        return try {
            extract(content, limit) ?: content.take(limit)
        } catch (e: Exception) {
            content.take(limit)
        }
    }

    private fun extract(content: String, limit: Int): String? {
        val sb = StringBuilder()
        factory.createParser(content).use { p ->
            if (p.nextToken() != JsonToken.START_OBJECT) return null
            if (p.nextToken() != JsonToken.FIELD_NAME || p.currentName != "root") return null
            while (p.nextToken() != null) {
                if (p.currentToken != JsonToken.FIELD_NAME) continue
                when (p.currentName) {
                    "text" -> if (p.nextToken() == JsonToken.VALUE_STRING) sb.append(p.text)
                    "type" -> if (p.nextToken() == JsonToken.VALUE_STRING && p.text !in inlineTypes) sb.append(' ')
                }
                // 공백 정리 후에도 충분히 모였으면 나머지는 읽지 않는다
                if (sb.length > limit * 2 + 64 && normalize(sb).length >= limit) break
            }
        }
        return normalize(sb).take(limit)
    }

    private fun normalize(sb: CharSequence): String = sb.toString().replace(WHITESPACE, " ").trim()
}
