package com.project.byeoldori.education.program.domain

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

/**
 * 교육 프로그램 퀴즈 응시 1회분.
 * 채점은 서버가 저장된 프로그램의 quiz 스텝을 기준으로 다시 한다(클라이언트가 보낸 정오답은 믿지 않는다).
 * 같은 사용자가 여러 번 응시할 수 있고, 매 응시를 그대로 남겨 학습 이력과 문항 통계에 쓴다.
 */
@Document("education_quiz_attempts")
@CompoundIndexes(
    CompoundIndex(name = "program_created", def = "{'programId': 1, 'createdAt': -1}"),
    CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
)
class QuizAttempt(
    @Id
    var id: String? = null,

    var programId: String,

    var userId: Long,

    var answers: List<QuizAnswer> = emptyList(),

    var score: Int = 0,

    var total: Int = 0,

    @CreatedDate
    var createdAt: LocalDateTime? = null
)

/** choice 가 null 이면 해당 문항을 풀지 않은 것(오답 처리). */
data class QuizAnswer(
    val quizId: String,
    val choice: Int?,
    val correct: Boolean
)
