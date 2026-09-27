package com.project.byeoldori.education.program.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class QuizAnswerRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val quizId: String,
    // null = 풀지 않고 넘어감
    val choice: Int? = null
)

data class SubmitQuizRequest(
    @field:Valid
    @field:Size(max = 200, message = "응답은 최대 200개입니다.")
    val answers: List<QuizAnswerRequest> = emptyList()
)

data class QuizResultItem(
    val quizId: String,
    val choice: Int?,
    val correct: Boolean,
    val answerIndex: Int
)

data class QuizResultResponse(
    // 미발행 프로그램(작성자·관리자 미리보기)은 채점만 하고 저장하지 않아 null
    val attemptId: String?,
    val recorded: Boolean,
    val score: Int,
    val total: Int,
    val results: List<QuizResultItem>
)

data class QuizQuestionStats(
    val quizId: String,
    val question: String,
    val choices: List<String>,
    val answerIndex: Int,
    val answered: Int,
    val correct: Int,
    // choices 와 같은 길이. 문항이 수정돼 보기 수가 줄었으면 범위 밖 응답은 세지 않는다.
    val choiceCounts: List<Int>
)

data class QuizStatsResponse(
    val programId: String,
    val attempts: Long,
    val uniqueUsers: Int,
    // 응시당 정답률(score/total)의 평균, 0.0~1.0. 응시가 없으면 null
    val averageRate: Double?,
    // 통계 계산에 쓴 최근 응시 수(상한 적용)
    val sampled: Int,
    val questions: List<QuizQuestionStats>
)

data class MyQuizAttemptResponse(
    val attemptId: String?,
    val programId: String,
    // 프로그램이 삭제됐으면 null
    val programTitle: String?,
    val score: Int,
    val total: Int,
    val createdAt: LocalDateTime?
)
