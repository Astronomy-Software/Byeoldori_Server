package com.project.byeoldori.education.program.service

import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.UnauthorizedException
import com.project.byeoldori.community.post.repository.EducationPostRepository
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.dto.CreateProgramRequest
import com.project.byeoldori.education.program.dto.UpdateProgramRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.star.service.CONTENT_TARGET_MAX_LENGTH
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.repository.Query
import java.time.LocalDateTime
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
    private val userRepo = mock(UserRepository::class.java)
    private val service = EducationProgramService(
        repo, systemConfig, mock(MongoTemplate::class.java), quizAttemptRepo, educationPostRepo, userRepo
    )

    private val author = User(id = 1, email = "a@test.com", passwordHash = "x", name = "작성자", phone = "010", nickname = "작성자")
    private val other = User(id = 2, email = "o@test.com", passwordHash = "x", name = "타인", phone = "010", nickname = "타인")
    private val admin = User(id = 3, email = "m@test.com", passwordHash = "x", name = "관리자", phone = "010", nickname = "관리자")
        .apply { roles = mutableSetOf("USER", "ADMIN") }

    @BeforeEach
    fun setUp() {
        `when`(repo.save(any(EducationProgram::class.java))).thenAnswer { it.arguments[0] }
        for (u in listOf(author, other, admin)) `when`(userRepo.findById(u.id)).thenReturn(Optional.of(u))
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
            // 비로그인(만료 토큰 포함)은 401 이어야 웹이 토큰을 갱신하고 재시도한다. 로그인한 타인은 403.
            assertThatThrownBy { service.get("prog-1", null) }.isInstanceOf(UnauthorizedException::class.java)
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

    @Test
    fun `검수 필수일 때 검수 대기 중 작성자가 고치면 DRAFT 로 돌아가 다시 검수를 받아야 한다`() {
        moderation(true)
        program(ProgramStatus.PREVIEW)
        val res = service.update("prog-1", UpdateProgramRequest(title = "바뀐 제목"), author)
        assertThat(res.title).isEqualTo("바뀐 제목")
        assertThat(res.status).isEqualTo(ProgramStatus.DRAFT)
        // 관리자가 바로 발행하려 해도 검수 요청 전이라 막힌다
        assertThatThrownBy { service.publish("prog-1", admin) }.isInstanceOf(InvalidInputException::class.java)
    }

    @Test
    fun `작성자가 직접 발행할 수 있으면 검수 대기 중 수정해도 PREVIEW 를 유지한다`() {
        moderation(false)
        program(ProgramStatus.PREVIEW)
        assertThat(service.update("prog-1", UpdateProgramRequest(title = "새 제목"), author).status)
            .isEqualTo(ProgramStatus.PREVIEW)

        // 관리자 본인이 작성자면 검수 필수여도 그대로 발행할 수 있으므로 PREVIEW 유지
        moderation(true)
        val adminOwned = EducationProgram(id = "prog-2", title = "관리자 글", authorId = admin.id, status = ProgramStatus.PREVIEW)
        `when`(repo.findById("prog-2")).thenReturn(Optional.of(adminOwned))
        assertThat(service.update("prog-2", UpdateProgramRequest(title = "새 제목"), admin).status)
            .isEqualTo(ProgramStatus.PREVIEW)
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
    fun `steps 에 Long 범위를 넘는 정수가 있으면 500 이 아니라 400`() {
        val big = listOf(mapOf<String, Any?>("id" to "t1", "type" to "show-text", "n" to java.math.BigInteger("99999999999999999999")))
        assertThatThrownBy { service.create(author, CreateProgramRequest(title = "t", steps = big)) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(repo, never()).save(any(EducationProgram::class.java))
    }

    @Test
    fun `steps 의 type 이 숫자여도 500 이 아니라 그대로 저장한다`() {
        val steps = listOf(mapOf<String, Any?>("id" to "t1", "type" to 1))
        assertThat(service.create(author, CreateProgramRequest(title = "t", steps = steps)).status).isEqualTo(ProgramStatus.DRAFT)
    }

    // ── targets: 라벨 길이 상한·정리 ──

    @Test
    fun `대상 천체 라벨이 길이 상한을 넘으면 생성·수정 모두 저장하지 않는다`() {
        val long = listOf("a".repeat(CONTENT_TARGET_MAX_LENGTH + 1))
        assertThatThrownBy { service.create(author, CreateProgramRequest(title = "t", targets = long)) }
            .isInstanceOf(InvalidInputException::class.java)

        program(ProgramStatus.DRAFT)
        assertThatThrownBy { service.update("prog-1", UpdateProgramRequest(targets = long), author) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(repo, never()).save(any(EducationProgram::class.java))
    }

    @Test
    fun `대상 천체 라벨은 공백을 지우고 빈 값·중복을 뺀 뒤 저장한다`() {
        val captured = mutableListOf<EducationProgram>()
        `when`(repo.save(any(EducationProgram::class.java))).thenAnswer { (it.arguments[0] as EducationProgram).also { p -> captured += p } }
        service.create(author, CreateProgramRequest(title = "t", targets = listOf(" Moon", "Moon ", "", "  ", "M42", "a".repeat(CONTENT_TARGET_MAX_LENGTH))))
        assertThat(captured.single().targets).containsExactly("Moon", "M42", "a".repeat(CONTENT_TARGET_MAX_LENGTH))

        val p = program(ProgramStatus.DRAFT)
        service.update("prog-1", UpdateProgramRequest(targets = listOf(" 시리우스 ", "시리우스")), author)
        assertThat(p.targets).containsExactly("시리우스")

        // 대상 천체 중복은 대소문자·악센트를 무시한다(게시글 targets 와 같은 규칙)
        service.update("prog-1", UpdateProgramRequest(targets = listOf("Moon", "moon", "Vega", "Véga")), author)
        assertThat(p.targets).containsExactly("Moon", "Vega")
    }

    @Test
    fun `새 프로그램은 DRAFT 로 만들어진다`() {
        val res = service.create(author, CreateProgramRequest(title = "작은 프로그램"))
        assertThat(res.status).isEqualTo(ProgramStatus.DRAFT)
        assertThat(res.authorId).isEqualTo(author.id)
        assertThat(res.authorName).isEqualTo("작성자")
    }

    // ── 작성자 표시 이름: 저장된 생성 시점 닉네임 대신 현재 사용자 정보 ──

    @Test
    fun `상세의 작성자 이름은 현재 닉네임이고 탈퇴한 작성자는 탈퇴한 사용자로 보인다`() {
        val p = program(ProgramStatus.PUBLISHED)
        p.authorName = "옛닉네임"
        assertThat(service.get("prog-1", null).authorName).isEqualTo("작성자")

        val deleted = User(id = 1, email = "deleted+1@deleted.byeoldori", passwordHash = "DELETED", name = "탈퇴한 사용자", phone = "")
            .apply { deletedAt = LocalDateTime.now() }
        `when`(userRepo.findById(1L)).thenReturn(Optional.of(deleted))
        assertThat(service.get("prog-1", null).authorName).isEqualTo("탈퇴한 사용자")

        `when`(userRepo.findById(1L)).thenReturn(Optional.empty())
        assertThat(service.get("prog-1", null).authorName).isEqualTo("탈퇴한 사용자")
    }

    @Test
    fun `공개 목록은 steps 를 뺀 조회를 쓰고 작성자 이름을 한 번에 조회해 채운다`() {
        val deleted = User(id = 9, email = "deleted+9@deleted.byeoldori", passwordHash = "DELETED", name = "탈퇴한 사용자", phone = "")
            .apply { deletedAt = LocalDateTime.now() }
        val pageable = PageRequest.of(0, 20)
        val rows = listOf(
            EducationProgram(id = "a", title = "A", authorId = author.id, authorName = "옛닉네임", status = ProgramStatus.PUBLISHED),
            EducationProgram(id = "b", title = "B", authorId = deleted.id, authorName = "탈퇴 전 닉네임", status = ProgramStatus.PUBLISHED),
            EducationProgram(id = "c", title = "C", authorId = author.id, status = ProgramStatus.PUBLISHED),
        )
        `when`(repo.findSummariesByStatus(ProgramStatus.PUBLISHED, pageable)).thenReturn(PageImpl(rows, pageable, 3))
        `when`(userRepo.findAllById(setOf(author.id, deleted.id))).thenReturn(listOf(author, deleted))

        val res = service.listPublished(pageable)
        assertThat(res.content.map { it.authorName }).containsExactly("작성자", "탈퇴한 사용자", "작성자")
    }

    @Test
    fun `authorId 0 인 기본 제공 프로그램은 탈퇴자가 아니라 저장된 작성자 이름을 보여준다`() {
        val pageable = PageRequest.of(0, 20)
        val system = EducationProgram(id = "orion-101", title = "오리온", authorId = 0, authorName = "별도리", status = ProgramStatus.PUBLISHED)
        val rows = listOf(system, EducationProgram(id = "a", title = "A", authorId = author.id, status = ProgramStatus.PUBLISHED))
        `when`(repo.findSummariesByStatus(ProgramStatus.PUBLISHED, pageable)).thenReturn(PageImpl(rows, pageable, 2))
        `when`(userRepo.findAllById(setOf(author.id))).thenReturn(listOf(author))

        assertThat(service.listPublished(pageable).content.map { it.authorName }).containsExactly("별도리", "작성자")

        `when`(repo.findById("orion-101")).thenReturn(Optional.of(system))
        assertThat(service.get("orion-101", null).authorName).isEqualTo("별도리")
        verify(userRepo, never()).findById(0L)
    }

    @Test
    fun `목록 조회 쿼리는 steps 를 제외하고 status·authorId 인덱스를 둔다`() {
        for (name in listOf("findSummariesByStatus", "findSummariesByAuthorId")) {
            val q = EducationProgramRepository::class.java.methods.first { it.name == name }.getAnnotation(Query::class.java)
            assertThat(q.fields.replace(" ", "")).isEqualTo("{'steps':0}")
        }
        val defs = EducationProgram::class.java.getAnnotation(CompoundIndexes::class.java).value.map { it.def.replace(" ", "") }
        assertThat(defs).containsExactlyInAnyOrder("{'status':1,'updatedAt':-1}", "{'authorId':1,'updatedAt':-1}")
    }
}
