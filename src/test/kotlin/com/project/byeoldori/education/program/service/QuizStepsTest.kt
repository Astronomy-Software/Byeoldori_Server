package com.project.byeoldori.education.program.service

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.education.program.domain.QuizAnswer
import com.project.byeoldori.education.program.domain.QuizAttempt
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.bson.Document
import org.junit.jupiter.api.Test

/** quiz 스텝 파싱·검증과 통계 집계 (순수 로직, Spring 컨텍스트 불필요). */
class QuizStepsTest {

    private fun quiz(id: String, answer: Any? = 1, choices: List<Any?> = listOf("직녀성", "견우성", "데네브")) =
        Document(mapOf("id" to id, "type" to "quiz", "question" to "여름철 대삼각형이 아닌 별은?", "choices" to choices, "answerIndex" to answer))

    private val text = Document(mapOf("id" to "t1", "type" to "show-text", "text" to "안녕"))

    @Test
    fun `최상위 quiz 스텝만 순서대로 추출한다`() {
        val specs = QuizSteps.extract(listOf(text, quiz("q1"), quiz("q2", answer = 0)))
        assertThat(specs.map { it.id }).containsExactly("q1", "q2")
        assertThat(specs[1].answerIndex).isEqualTo(0)
    }

    @Test
    fun `JSON 숫자가 Long 이나 Double 로 와도 정답 번호를 읽는다`() {
        assertThat(QuizSteps.extract(listOf(quiz("q", answer = 2L))).single().answerIndex).isEqualTo(2)
        assertThat(QuizSteps.extract(listOf(quiz("q", answer = 2.0))).single().answerIndex).isEqualTo(2)
    }

    @Test
    fun `정답 번호가 범위 밖이거나 소수면 형식 오류로 막는다`() {
        listOf<Any?>(3, -1, 1.5, null, "1").forEach { bad ->
            assertThatThrownBy { QuizSteps.validate(listOf(quiz("q", answer = bad))) }
                .`as`("answerIndex=%s", bad)
                .isInstanceOf(InvalidInputException::class.java)
        }
    }

    @Test
    fun `보기는 2개 이상 6개 이하, 빈 보기는 허용하지 않는다`() {
        assertThatThrownBy { QuizSteps.validate(listOf(quiz("q", answer = 0, choices = listOf("하나")))) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { QuizSteps.validate(listOf(quiz("q", answer = 0, choices = List(7) { "보기$it" }))) }
            .isInstanceOf(InvalidInputException::class.java)
        assertThatThrownBy { QuizSteps.validate(listOf(quiz("q", answer = 0, choices = listOf("가", " ")))) }
            .isInstanceOf(InvalidInputException::class.java)
    }

    @Test
    fun `스텝 id 가 중복되면 막는다`() {
        assertThatThrownBy { QuizSteps.validate(listOf(quiz("same"), quiz("same"))) }
            .isInstanceOf(InvalidInputException::class.java)
            .hasMessageContaining("2번")
    }

    @Test
    fun `복합 스텝 안의 퀴즈는 막는다`() {
        val composite = Document(mapOf("type" to "composite", "steps" to listOf(mapOf("type" to "quiz"))))
        assertThatThrownBy { QuizSteps.validate(listOf(text, composite)) }
            .isInstanceOf(InvalidInputException::class.java)
            .hasMessageContaining("복합")
    }

    @Test
    fun `퀴즈가 없는 프로그램은 검증을 통과한다`() {
        QuizSteps.validate(listOf(text))
        assertThat(QuizSteps.extract(listOf(text))).isEmpty()
    }

    @Test
    fun `통계는 현재 문항 기준으로 정답률과 보기 분포를 센다`() {
        val specs = QuizSteps.extract(listOf(quiz("q1", answer = 1), quiz("q2", answer = 0)))
        val attempts = listOf(
            attempt(1, QuizAnswer("q1", 1, true), QuizAnswer("q2", 0, true)),
            attempt(2, QuizAnswer("q1", 0, false), QuizAnswer("q2", null, false)),
            // 문항 수정 전 응답: 사라진 문항 id·범위 밖 보기는 세지 않는다
            attempt(2, QuizAnswer("old", 0, true), QuizAnswer("q1", 9, false)),
        )
        val stats = computeStats("p", specs, attempts, totalAttempts = 3)

        assertThat(stats.attempts).isEqualTo(3)
        assertThat(stats.uniqueUsers).isEqualTo(2)
        val q1 = stats.questions[0]
        assertThat(q1.answered).isEqualTo(2)
        assertThat(q1.correct).isEqualTo(1)
        assertThat(q1.choiceCounts).containsExactly(1, 1, 0)
        val q2 = stats.questions[1]
        assertThat(q2.answered).isEqualTo(1) // 건너뛴 응답(null)은 제외
        assertThat(q2.correct).isEqualTo(1)
    }

    @Test
    fun `응시가 없으면 평균 정답률은 null`() {
        val stats = computeStats("p", QuizSteps.extract(listOf(quiz("q1"))), emptyList(), 0)
        assertThat(stats.averageRate).isNull()
        assertThat(stats.questions.single().choiceCounts).containsExactly(0, 0, 0)
    }

    private fun attempt(userId: Long, vararg answers: QuizAnswer) = QuizAttempt(
        programId = "p",
        userId = userId,
        answers = answers.toList(),
        score = answers.count { it.correct },
        total = 2
    )
}
