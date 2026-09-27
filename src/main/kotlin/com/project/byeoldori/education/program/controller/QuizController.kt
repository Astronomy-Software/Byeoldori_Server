package com.project.byeoldori.education.program.controller

import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.education.program.dto.MyQuizAttemptResponse
import com.project.byeoldori.education.program.dto.QuizResultResponse
import com.project.byeoldori.education.program.dto.QuizStatsResponse
import com.project.byeoldori.education.program.dto.SubmitQuizRequest
import com.project.byeoldori.education.program.service.QuizService
import com.project.byeoldori.user.entity.User
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*

// 모든 경로가 SecurityConfig 의 permitAll 패턴(/education/programs, /education/programs/*)에
// 걸리지 않는 깊이라 anyRequest().authenticated() 로 로그인이 강제된다.
@RestController
@RequestMapping("/education")
@Tag(name = "EducationQuiz", description = "교육 프로그램 퀴즈 응시·통계 API")
class QuizController(
    private val quizService: QuizService
) {
    @PostMapping("/programs/{id}/quiz-attempts")
    @Operation(
        summary = "퀴즈 응시 제출",
        description = "서버가 저장된 문항으로 채점합니다. 발행된 프로그램만 기록되고, 미발행(작성자·관리자 미리보기)은 채점만 합니다."
    )
    fun submit(
        @PathVariable id: String,
        @Valid @RequestBody req: SubmitQuizRequest,
        @RequestAttribute("currentUser") user: User
    ): QuizResultResponse = quizService.submit(id, user, req)

    @GetMapping("/programs/{id}/quiz-stats")
    @Operation(summary = "퀴즈 통계", description = "문항별 정답률과 보기 분포. 작성자 또는 관리자만.")
    fun stats(
        @PathVariable id: String,
        @RequestAttribute("currentUser") user: User
    ): QuizStatsResponse = quizService.stats(id, user)

    @GetMapping("/quiz-attempts/me")
    @Operation(summary = "내 퀴즈 응시 이력", description = "최신순.")
    fun myAttempts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestAttribute("currentUser") user: User
    ): PageResponse<MyQuizAttemptResponse> =
        quizService.myAttempts(
            user,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 50), Sort.by(Sort.Direction.DESC, "createdAt"))
        )
}
