package com.project.byeoldori.education.program.service

import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.community.post.repository.EducationPostRepository
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.dto.CreateProgramRequest
import com.project.byeoldori.education.program.dto.UpdateProgramRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.mongodb.core.MongoTemplate
import java.util.Optional

/**
 * 교육 프로그램 상태 전이(DRAFT → PREVIEW → PUBLISHED, 반려)와 권한·공개 범위 규칙.
 * 리포지토리는 Mongo 라 Mockito 로 대신한다(Docker 불필요).
 */
class EducationProgramServiceTest {

    private val repo = mock(EducationProgramRepository::class.java)
    private val systemConfig = mock(SystemConfigService::class.java)
    private val quizAttemptRepo = mock(QuizAttemptRepository::class.java)
    private val educationPostRepo = mock(EducationPostRepository::class.java)
    private val service = EducationProgramService(
        repo, systemConfig, mock(MongoTemplate::class.java), quizAttemptRepo, educationPostRepo
    )

    private val author = User(id = 1, email = "a@test.com", passwordHash = "x", name = "작성자", phone = "010", nickname = "작성자")
    private val other = User(id = 2, email = "o@test.com", passwordHash = "x", name = "타인", phone = "010", nickname = "타인")
    private val admin = User(id = 3, email = "m@test.com", passwordHash = "x", name = "관리자", phone = "010", nickname = "관리자")
        .apply { roles = mutableSetOf("USER", "ADMIN") }

    @BeforeEach
    fun setUp() {
        `when`(repo.save(any(EducationProgram::class.java))).thenAnswer { it.arguments[0] }
    }

    private fun program(status: ProgramStatus, rejectReason: String? = null): EducationProgram {
        val p = EducationProgram(id = "prog-1", title = "여름철 별자리", authorId = author.id, status = status, rejectReason = rejectReason)
        `when`(repo.findById("prog-1")).thenReturn(Optional.of(p))
        return p
    }

    private fun moderation(required: Boolean) {
        `when`(systemConfig.moderationRequired()).thenReturn(required)
    }

    // ── get: 미발행 조회 차단 ──

    @Test
    fun `미발행 프로그램은 비로그인과 타인에게 막힌다`() {
        for (status in listOf(ProgramStatus.DRAFT, ProgramStatus.PREVIEW)) {
            program(status)
            assertThatThrownBy { service.get("prog-1", null) }.isInstanceOf(ForbiddenException::class.java)
            assertThatThrownBy { service.get("prog-1", other) }.isInstanceOf(ForbiddenException::class.java)
            assertThat(service.get("prog-1", author).status).isEqualTo(status)
            assertThat(service.get("prog-1", admin).status).isEqualTo(status)
        }
    }

    @Test
    fun `발행된 프로그램은 비로그인도 조회한다`() {
        program(ProgramStatus.PUBLISHED)
        assertThat(service.get("prog-1", null).status).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(service.get("prog-1", other).status).isEqualTo(ProgramStatus.PUBLISHED)
    }

    // ── publish: moderation 토글 ──

    @Test
    fun `검수 필수면 작성자는 발행 못 하고 관리자만 발행한다`() {
        moderation(true)
        program(ProgramStatus.PREVIEW, rejectReason = "예전 사유")
        assertThatThrownBy { service.publish("prog-1", author) }.isInstanceOf(ForbiddenException::class.java)

        val res = service.publish("prog-1", admin)
        assertThat(res.status).isEqualTo(ProgramStatus.PUBLISHED)
        assertThat(res.rejectReason).isNull()
    }

    @Test
    fun `검수 해제면 작성자도 발행하지만 타인은 못 한다`() {
        moderation(false)
        program(ProgramStatus.PREVIEW)
        assertThatThrownBy { service.publish("prog-1", other) }.isInstanceOf(ForbiddenException::class.java)
        assertThat(service.publish("prog-1", author).status).isEqualTo(ProgramStatus.PUBLISHED)
    }

    @Test
    fun `검수 요청 전(DRAFT)에는 관리자도 발행할 수 없다`() {
        moderation(true)
        program(ProgramStatus.DRAFT)
        assertThatThrownBy { service.publish("prog-1", admin) }.isInstanceOf(InvalidInputException::class.java)
        verify(repo, never()).save(any(EducationProgram::class.java))
    }

    // ── update ──

    @Test
    fun `발행된 프로그램은 수정할 수 없고 타인은 어떤 상태든 수정할 수 없다`() {
        program(ProgramStatus.PUBLISHED)
        assertThatThrownBy { service.update("prog-1", UpdateProgramRequest(title = "새 제목"), author) }
            .isInstanceOf(InvalidInputException::class.java)

        program(ProgramStatus.DRAFT)
        assertThatThrownBy { service.update("prog-1", UpdateProgramRequest(title = "새 제목"), other) }
            .isInstanceOf(ForbiddenException::class.java)
        assertThat(service.update("prog-1", UpdateProgramRequest(title = "새 제목"), author).title).isEqualTo("새 제목")
    }

    // ── submit / reject ──

    @Test
    fun `검수 요청은 DRAFT 에서만 되고 반려 사유를 비운다`() {
        program(ProgramStatus.DRAFT, rejectReason = "사진 출처 누락")
        assertThatThrownBy { service.submit("prog-1", other) }.isInstanceOf(ForbiddenException::class.java)

        val res = service.submit("prog-1", author)
        assertThat(res.status).isEqualTo(ProgramStatus.PREVIEW)
        assertThat(res.rejectReason).isNull()

        program(ProgramStatus.PREVIEW)
        assertThatThrownBy { service.submit("prog-1", author) }.isInstanceOf(InvalidInputException::class.java)
    }

    @Test
    fun `반려는 관리자만, PREVIEW 에서만 되고 공백 사유는 null 로 저장한다`() {
        program(ProgramStatus.PREVIEW)
        assertThatThrownBy { service.reject("prog-1", author, "사유") }.isInstanceOf(ForbiddenException::class.java)

        val res = service.reject("prog-1", admin, "  ")
        assertThat(res.status).isEqualTo(ProgramStatus.DRAFT)
        assertThat(res.rejectReason).isNull()

        program(ProgramStatus.PREVIEW)
        assertThat(service.reject("prog-1", admin, "  출처 표기 필요 ").rejectReason).isEqualTo("출처 표기 필요")

        program(ProgramStatus.DRAFT)
        assertThatThrownBy { service.reject("prog-1", admin, "사유") }.isInstanceOf(InvalidInputException::class.java)
    }

    // ── delete: 연쇄 정리 ──

    @Test
    fun `삭제하면 응시 기록을 지우고 연결된 게시글의 programId 를 비운다`() {
        val p = program(ProgramStatus.PUBLISHED)
        assertThatThrownBy { service.delete("prog-1", other) }.isInstanceOf(ForbiddenException::class.java)
        verify(repo, never()).delete(any(EducationProgram::class.java))

        service.delete("prog-1", author)
        verify(repo).delete(p)
        verify(quizAttemptRepo).deleteByProgramId("prog-1")
        verify(educationPostRepo).clearProgramId("prog-1")
    }

    // ── create: steps 512KB 상한 ──

    @Test
    fun `steps 직렬화가 512KB 를 넘으면 저장하지 않는다`() {
        val huge = listOf(mapOf<String, Any?>("id" to "t1", "type" to "show-text", "text" to "가".repeat(200_000)))
        assertThatThrownBy { service.create(author, CreateProgramRequest(title = "큰 프로그램", steps = huge)) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(repo, never()).save(any(EducationProgram::class.java))
    }

    @Test
    fun `새 프로그램은 DRAFT 로 만들어진다`() {
        val res = service.create(author, CreateProgramRequest(title = "작은 프로그램"))
        assertThat(res.status).isEqualTo(ProgramStatus.DRAFT)
        assertThat(res.authorId).isEqualTo(author.id)
    }
}
