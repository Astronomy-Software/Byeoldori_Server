package com.project.byeoldori.education.program.dto

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 교육 프로그램 응답 매핑. 저장된 authorName 대신 호출부가 넘긴 현재 닉네임을 쓰는지, steps 가 그대로 전달되는지 확인한다.
 */
class ProgramDtosTest {

    private val program = EducationProgram(
        id = "66f0a1b2c3d4e5f6a7b8c9d0", title = "달의 위상", subtitle = "한 달의 변화", difficulty = EducationDifficulty.BEGINNER,
        targets = listOf("달"), authorId = 7, authorName = "옛닉네임", status = ProgramStatus.PREVIEW, schemaVersion = 2,
        steps = listOf(Document("type", "narration").append("text", "안녕"), Document("type", "quiz")),
        viewCount = 123, rejectReason = "오타", createdAt = LocalDateTime.of(2026, 9, 1, 0, 0), updatedAt = LocalDateTime.of(2026, 9, 27, 12, 0),
    )

    @Test
    fun `요약 응답 - authorName 은 저장값이 아니라 인자로 받은 현재 이름`() {
        val res = ProgramSummaryResponse.from(program, "새닉네임")

        assertThat(res.id).isEqualTo("66f0a1b2c3d4e5f6a7b8c9d0")
        assertThat(res.title).isEqualTo("달의 위상")
        assertThat(res.difficulty).isEqualTo(EducationDifficulty.BEGINNER)
        assertThat(res.status).isEqualTo(ProgramStatus.PREVIEW)
        assertThat(res.authorName).isEqualTo("새닉네임")
        assertThat(res.viewCount).isEqualTo(123)
        assertThat(res.updatedAt).isEqualTo(LocalDateTime.of(2026, 9, 27, 12, 0))
        assertThat(res.rejectReason).isEqualTo("오타")
    }

    @Test
    fun `요약 응답 - 작성자가 탈퇴해 이름이 없으면 null`() {
        assertThat(ProgramSummaryResponse.from(program, null).authorName).isNull()
    }

    @Test
    fun `상세 응답 - steps·schemaVersion·authorId 를 그대로 싣는다`() {
        val res = ProgramDetailResponse.from(program, "새닉네임")

        assertThat(res.id).isEqualTo(program.id)
        assertThat(res.subtitle).isEqualTo("한 달의 변화")
        assertThat(res.schemaVersion).isEqualTo(2)
        assertThat(res.steps).hasSize(2)
        assertThat(res.steps[0].getString("type")).isEqualTo("narration")
        assertThat(res.steps[0].getString("text")).isEqualTo("안녕")
        assertThat(res.authorId).isEqualTo(7)
        assertThat(res.authorName).isEqualTo("새닉네임")
        assertThat(res.rejectReason).isEqualTo("오타")
        assertThat(res.status).isEqualTo(ProgramStatus.PREVIEW)
    }

    @Test
    fun `새 프로그램 기본값 - DRAFT, schemaVersion 1, steps 비어 있음`() {
        val p = EducationProgram(title = "t", authorId = 1)
        assertThat(p.status).isEqualTo(ProgramStatus.DRAFT)
        assertThat(p.schemaVersion).isEqualTo(1)
        assertThat(p.steps).isEmpty()
        assertThat(p.viewCount).isEqualTo(0)
        assertThat(ProgramDetailResponse.from(p, null).id).isNull()
        assertThat(ProgramSummaryResponse.from(p, null).rejectReason).isNull()
    }

    @Test
    fun `퀴즈 응답 DTO 기본값 - answers 는 빈 목록, choice 는 null(미응답)`() {
        assertThat(SubmitQuizRequest().answers).isEmpty()
        assertThat(QuizAnswerRequest(quizId = "q1").choice).isNull()
    }
}
