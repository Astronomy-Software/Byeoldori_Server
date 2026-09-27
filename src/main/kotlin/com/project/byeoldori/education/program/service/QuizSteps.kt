package com.project.byeoldori.education.program.service

import com.project.byeoldori.common.exception.InvalidInputException
import org.bson.Document

/** 프로그램 steps 안의 quiz 스텝 한 개 (채점 기준). */
data class QuizSpec(
    val id: String,
    val question: String,
    val choices: List<String>,
    val answerIndex: Int
)

/**
 * 프로그램 steps(org.bson.Document 목록)에서 quiz 스텝을 읽고 검증한다.
 *
 * 프론트 스텝 계약: { id, type: "quiz", question, choices: string[], answerIndex, explanation? }
 * quiz 는 최상위 스텝에만 둘 수 있다. composite(동시 실행) 안에 넣으면 재생기가
 * 응답을 기다릴 수 없어 막는다.
 */
object QuizSteps {
    const val TYPE = "quiz"
    const val MIN_CHOICES = 2
    const val MAX_CHOICES = 6
    private const val MAX_QUESTION = 300
    private const val MAX_CHOICE = 120

    /** 저장 시 호출 — 잘못된 quiz 스텝이 있으면 사용자에게 보여줄 메시지로 400. */
    fun validate(steps: List<Document>) {
        val seen = mutableSetOf<String>()
        steps.forEachIndexed { i, step ->
            val no = i + 1
            if (step.getString("type") == "composite") {
                val children = step["steps"] as? List<*> ?: emptyList<Any>()
                if (children.any { (it as? Map<*, *>)?.get("type") == TYPE }) {
                    throw InvalidInputException("${no}번 스텝: 퀴즈는 복합 스텝 안에 넣을 수 없습니다.")
                }
            }
            if (step.getString("type") != TYPE) return@forEachIndexed
            val spec = parse(step) ?: throw InvalidInputException(
                "${no}번 퀴즈: 문제, 보기(${MIN_CHOICES}~${MAX_CHOICES}개), 정답을 모두 입력해주세요."
            )
            if (spec.question.length > MAX_QUESTION) {
                throw InvalidInputException("${no}번 퀴즈: 문제는 ${MAX_QUESTION}자를 넘을 수 없습니다.")
            }
            if (spec.choices.any { it.length > MAX_CHOICE }) {
                throw InvalidInputException("${no}번 퀴즈: 보기는 ${MAX_CHOICE}자를 넘을 수 없습니다.")
            }
            if (!seen.add(spec.id)) {
                throw InvalidInputException("${no}번 퀴즈: 스텝 id가 중복됩니다.")
            }
        }
    }

    /** 채점용 — 최상위 quiz 스텝만, 형식이 온전한 것만 순서대로 돌려준다. */
    fun extract(steps: List<Document>): List<QuizSpec> =
        steps.filter { it.getString("type") == TYPE }.mapNotNull { parse(it) }

    private fun parse(step: Document): QuizSpec? {
        val id = (step["id"] as? String)?.takeIf { it.isNotBlank() } ?: return null
        val question = (step["question"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawChoices = step["choices"] as? List<*> ?: return null
        val choices = rawChoices.map { (it as? String)?.trim() ?: return null }
        if (choices.size !in MIN_CHOICES..MAX_CHOICES || choices.any { it.isEmpty() }) return null
        // JSON 숫자는 Integer/Long/Double 어느 것으로도 들어올 수 있다
        val answer = (step["answerIndex"] as? Number) ?: return null
        val answerIndex = answer.toDouble()
        if (answerIndex != Math.floor(answerIndex)) return null
        val idx = answerIndex.toInt()
        if (idx !in choices.indices) return null
        return QuizSpec(id, question, choices, idx)
    }
}
