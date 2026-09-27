package com.project.byeoldori.education.program.service

import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.common.dto.toPageResponse
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.domain.QuizAnswer
import com.project.byeoldori.education.program.domain.QuizAttempt
import com.project.byeoldori.education.program.dto.MyQuizAttemptResponse
import com.project.byeoldori.education.program.dto.QuizQuestionStats
import com.project.byeoldori.education.program.dto.QuizResultItem
import com.project.byeoldori.education.program.dto.QuizResultResponse
import com.project.byeoldori.education.program.dto.QuizStatsResponse
import com.project.byeoldori.education.program.dto.SubmitQuizRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class QuizService(
    private val programRepo: EducationProgramRepository,
    private val attemptRepo: QuizAttemptRepository
) {
    companion object {
        // 문항 통계는 최근 응시분만으로 계산한다(전체 스캔 방지)
        private const val STATS_SAMPLE = 2000
    }

    private fun isAdmin(user: User): Boolean = user.roles.contains("ADMIN")

    private fun findProgram(id: String): EducationProgram =
        programRepo.findById(id).orElseThrow { NotFoundException(ErrorCode.EDU_PROGRAM_NOT_FOUND) }

    /**
     * 응답을 서버 기준으로 채점한다.
     * PUBLISHED 는 누구나(로그인 사용자) 응시·기록. 미발행은 작성자·관리자의 미리보기로 보고
     * 채점 결과만 돌려주고 저장하지 않는다(문항 통계 오염 방지).
     */
    fun submit(programId: String, user: User, req: SubmitQuizRequest): QuizResultResponse {
        val program = findProgram(programId)
        val published = program.status == ProgramStatus.PUBLISHED
        if (!published && program.authorId != user.id && !isAdmin(user)) {
            throw ForbiddenException("해당 프로그램에 응시할 권한이 없습니다.")
        }

        val specs = QuizSteps.extract(program.steps)
        if (specs.isEmpty()) throw InvalidInputException("퀴즈가 없는 프로그램입니다.")

        val byId = specs.associateBy { it.id }
        val unknown = req.answers.map { it.quizId }.filter { it !in byId }
        if (unknown.isNotEmpty()) {
            // 응시 도중 저작자가 문항을 지웠을 수 있다 — 새로고침을 안내한다
            throw InvalidInputException("프로그램이 수정되었습니다. 새로고침 후 다시 풀어주세요.")
        }
        // 같은 문항을 여러 번 보냈으면 마지막 응답을 쓴다
        val chosen = req.answers.associate { it.quizId to it.choice }

        val answers = specs.map { spec ->
            val choice = chosen[spec.id]?.takeIf { it in spec.choices.indices }
            QuizAnswer(spec.id, choice, choice == spec.answerIndex)
        }
        val score = answers.count { it.correct }

        val saved = if (published) {
            attemptRepo.save(
                QuizAttempt(
                    programId = programId,
                    userId = user.id,
                    answers = answers,
                    score = score,
                    total = specs.size
                )
            )
        } else null

        return QuizResultResponse(
            attemptId = saved?.id,
            recorded = saved != null,
            score = score,
            total = specs.size,
            results = specs.zip(answers).map { (spec, a) ->
                QuizResultItem(spec.id, a.choice, a.correct, spec.answerIndex)
            }
        )
    }

    /** 문항별 정답률·보기 분포. 작성자 또는 관리자만. */
    fun stats(programId: String, user: User): QuizStatsResponse {
        val program = findProgram(programId)
        if (program.authorId != user.id && !isAdmin(user)) {
            throw ForbiddenException("작성자 또는 관리자만 퀴즈 통계를 볼 수 있습니다.")
        }
        val page = attemptRepo.findByProgramId(
            programId,
            PageRequest.of(0, STATS_SAMPLE, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return computeStats(programId, QuizSteps.extract(program.steps), page.content, page.totalElements)
    }

    /** 내 응시 이력(최신순). */
    fun myAttempts(user: User, pageable: Pageable): PageResponse<MyQuizAttemptResponse> {
        val page = attemptRepo.findByUserId(user.id, pageable)
        val titles = programRepo.findAllById(page.content.map { it.programId }.distinct())
            .associate { it.id to it.title }
        return page.map {
            MyQuizAttemptResponse(
                attemptId = it.id,
                programId = it.programId,
                programTitle = titles[it.programId],
                score = it.score,
                total = it.total,
                createdAt = it.createdAt
            )
        }.toPageResponse()
    }
}

/** 순수 집계 — 현재 문항 기준으로 응시 기록을 센다(수정 전 문항 응답은 id가 맞을 때만 반영). */
internal fun computeStats(
    programId: String,
    specs: List<QuizSpec>,
    attempts: List<QuizAttempt>,
    totalAttempts: Long
): QuizStatsResponse {
    val questions = specs.map { spec ->
        val counts = IntArray(spec.choices.size)
        var answered = 0
        var correct = 0
        for (attempt in attempts) {
            val a = attempt.answers.firstOrNull { it.quizId == spec.id } ?: continue
            val choice = a.choice ?: continue
            if (choice !in counts.indices) continue
            answered++
            counts[choice]++
            if (choice == spec.answerIndex) correct++
        }
        QuizQuestionStats(spec.id, spec.question, spec.choices, spec.answerIndex, answered, correct, counts.toList())
    }
    val rates = attempts.filter { it.total > 0 }.map { it.score.toDouble() / it.total }
    return QuizStatsResponse(
        programId = programId,
        attempts = totalAttempts,
        uniqueUsers = attempts.map { it.userId }.distinct().size,
        averageRate = if (rates.isEmpty()) null else rates.average(),
        sampled = attempts.size,
        questions = questions
    )
}
