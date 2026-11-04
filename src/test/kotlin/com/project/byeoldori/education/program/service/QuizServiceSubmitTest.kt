package com.project.byeoldori.education.program.service

import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.domain.QuizAttempt
import com.project.byeoldori.education.program.dto.QuizAnswerRequest
import com.project.byeoldori.education.program.dto.SubmitQuizRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyCollection
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.repository.Query
import java.util.Optional

/**
 * 퀴즈 채점·응시 권한(QuizService.submit).
 * 미발행 응시 권한, 미리보기 미저장(통계 오염 방지), 범위 밖 보기·중복 응답·지워진 문항 처리를 확인한다.
 */
class QuizServiceSubmitTest {

    private val programRepo = mock(EducationProgramRepository::class.java)
    private val attemptRepo = mock(QuizAttemptRepository::class.java)
    private val service = QuizService(programRepo, attemptRepo)

    private val author = User(id = 1, email = "a@test.com", passwordHash = "x", name = "작성자", phone = "010", nickname = "작성자")
    private val other = User(id = 2, email = "o@test.com", passwordHash = "x", name = "타인", phone = "010", nickname = "타인")
    private val admin = User(id = 3, email = "m@test.com", passwordHash = "x", name = "관리자", phone = "010", nickname = "관리자")
        .apply { roles = mutableSetOf("USER", "ADMIN") }

    private fun quiz(id: String, answer: Int) = Document(
        mapOf("id" to id, "type" to "quiz", "question" to "질문 $id", "choices" to listOf("가", "나", "다"), "answerIndex" to answer)
    )

    private val text = Document(mapOf("id" to "t1", "type" to "show-text", "text" to "안녕"))

    @BeforeEach
    fun setUp() {
        `when`(attemptRepo.save(any(QuizAttempt::class.java))).thenAnswer {
            (it.arguments[0] as QuizAttempt).apply { id = "attempt-1" }
        }
    }

    private fun program(status: ProgramStatus, steps: List<Document> = listOf(quiz("q1", 1), quiz("q2", 0))) {
        val p = EducationProgram(id = "prog-1", title = "여름철 별자리", authorId = author.id, status = status, steps = steps)
        `when`(programRepo.findById("prog-1")).thenReturn(Optional.of(p))
    }

    private fun req(vararg answers: Pair<String, Int?>) =
        SubmitQuizRequest(answers.map { (id, choice) -> QuizAnswerRequest(id, choice) })

    @Test
    fun `미발행 프로그램에 타인이 응시하면 403`() {
        for (status in listOf(ProgramStatus.DRAFT, ProgramStatus.PREVIEW)) {
            program(status)
            assertThatThrownBy { service.submit("prog-1", other, req("q1" to 1)) }
                .isInstanceOf(ForbiddenException::class.java)
        }
        verify(attemptRepo, never()).save(any(QuizAttempt::class.java))
    }

    @Test
    fun `미발행 프로그램의 작성자·관리자 응시는 채점만 하고 저장하지 않는다`() {
        program(ProgramStatus.DRAFT)
        for (user in listOf(author, admin)) {
            val res = service.submit("prog-1", user, req("q1" to 1, "q2" to 0))
            assertThat(res.recorded).isFalse()
            assertThat(res.attemptId).isNull()
            assertThat(res.score).isEqualTo(2)
        }
        verify(attemptRepo, never()).save(any(QuizAttempt::class.java))
    }

    @Test
    fun `발행된 프로그램은 타인 응시도 저장하고 점수를 매긴다`() {
        program(ProgramStatus.PUBLISHED)
        val res = service.submit("prog-1", other, req("q1" to 1, "q2" to 2))

        assertThat(res.recorded).isTrue()
        assertThat(res.attemptId).isEqualTo("attempt-1")
        assertThat(res.score).isEqualTo(1)
        assertThat(res.total).isEqualTo(2)
        assertThat(res.results.map { it.correct }).containsExactly(true, false)
        verify(attemptRepo).save(any(QuizAttempt::class.java))
    }

    @Test
    fun `범위 밖 보기는 오답이고 choice 는 null 로 기록된다`() {
        program(ProgramStatus.PUBLISHED)
        val res = service.submit("prog-1", other, req("q1" to 99, "q2" to -1))

        assertThat(res.score).isZero()
        assertThat(res.results.map { it.choice }).containsExactly(null, null)
    }

    @Test
    fun `같은 문항을 여러 번 보내면 마지막 응답으로 채점한다`() {
        program(ProgramStatus.PUBLISHED)
        val res = service.submit("prog-1", other, req("q1" to 0, "q1" to 1))

        assertThat(res.results.first().choice).isEqualTo(1)
        assertThat(res.results.first().correct).isTrue()
        // 응답하지 않은 q2 는 null·오답
        assertThat(res.results[1].choice).isNull()
        assertThat(res.score).isEqualTo(1)
    }

    @Test
    fun `지워진 문항 id 가 섞이면 400 이고 저장하지 않는다`() {
        program(ProgramStatus.PUBLISHED)
        assertThatThrownBy { service.submit("prog-1", other, req("q1" to 1, "deleted" to 0)) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(attemptRepo, never()).save(any(QuizAttempt::class.java))
    }

    @Test
    fun `퀴즈가 없는 프로그램은 400`() {
        program(ProgramStatus.PUBLISHED, steps = listOf(text))
        assertThatThrownBy { service.submit("prog-1", other, req()) }
            .isInstanceOf(InvalidInputException::class.java)
    }

    @Test
    fun `내 응시 이력은 steps 없이 제목만 프로젝션해 읽는다`() {
        val pageable = PageRequest.of(0, 20)
        val attempts = listOf(
            QuizAttempt(id = "a1", programId = "prog-1", userId = other.id, score = 1, total = 2),
            QuizAttempt(id = "a2", programId = "prog-1", userId = other.id, score = 2, total = 2),
            QuizAttempt(id = "a3", programId = "gone", userId = other.id, score = 0, total = 1),
        )
        `when`(attemptRepo.findByUserId(other.id, pageable)).thenReturn(PageImpl(attempts, pageable, 3))
        `when`(programRepo.findTitlesByIdIn(listOf("prog-1", "gone")))
            .thenReturn(listOf(EducationProgram(id = "prog-1", title = "여름철 별자리", authorId = author.id)))

        val res = service.myAttempts(other, pageable)
        assertThat(res.content.map { it.programTitle }).containsExactly("여름철 별자리", "여름철 별자리", null)
        verify(programRepo, never()).findAllById(anyCollection())

        val q = EducationProgramRepository::class.java.methods.first { it.name == "findTitlesByIdIn" }.getAnnotation(Query::class.java)
        assertThat(q.fields).doesNotContain("steps")
        assertThat(q.fields.replace(" ", "")).contains("'title':1")
    }
}
