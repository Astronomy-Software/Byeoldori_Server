package com.project.byeoldori.star.service

import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.star.entity.ContentTarget
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyIterable
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

/**
 * 대상 천체 태그 저장: 라벨마다 content_target 행이 생기므로 개수·길이 상한을 두고, 공백은 정리한다.
 * 예전에는 targets 10만 개를 보내면 10만 행이 INSERT 됐고, 255자를 넘는 라벨 하나로 글 전체가 롤백됐다.
 */
class ContentTargetServiceTest {

    private val repo = mock(ContentTargetRepository::class.java)
    private val service = ContentTargetService(repo)

    @Suppress("UNCHECKED_CAST")
    private fun savedLabels(): List<String> {
        val captor = ArgumentCaptor.forClass(Iterable::class.java) as ArgumentCaptor<Iterable<ContentTarget>>
        verify(repo).saveAll(captor.capture())
        return captor.value.map { it.starObjectName }
    }

    @Test
    fun `앞뒤 공백을 지우고 빈 라벨과 중복을 걸러 순서대로 저장한다`() {
        service.upsertTargets(ContentType.REVIEW, 1L, listOf(" Moon", "Moon ", "  ", "", "M31", "Moon"))
        verify(repo).deleteAllByContent(ContentType.REVIEW, 1L)
        assertThat(savedLabels()).containsExactly("Moon", "M31")
    }

    @Test
    fun `개수 상한을 넘으면 기존 태그를 지우기 전에 거부한다`() {
        val tooMany = (1..CONTENT_TARGET_MAX_COUNT + 1).map { "star-$it" }
        assertThatThrownBy { service.upsertTargets(ContentType.EVENT, 1L, tooMany) }
            .isInstanceOf(InvalidInputException::class.java)
        verify(repo, never()).deleteAllByContent(ContentType.EVENT, 1L)
        verify(repo, never()).saveAll(anyIterable<ContentTarget>())
    }

    @Test
    fun `중복을 걸러낸 뒤 상한 이내면 통과한다`() {
        val dupes = (1..CONTENT_TARGET_MAX_COUNT).map { "star-$it" } + (1..100).map { "star-1" }
        service.upsertTargets(ContentType.EVENT, 1L, dupes)
        assertThat(savedLabels()).hasSize(CONTENT_TARGET_MAX_COUNT)
    }

    @Test
    fun `라벨 길이 상한을 넘으면 거부한다`() {
        assertThatThrownBy {
            service.upsertTargets(ContentType.EDUCATION, 1L, listOf("a".repeat(CONTENT_TARGET_MAX_LENGTH + 1)))
        }.isInstanceOf(InvalidInputException::class.java)

        service.upsertTargets(ContentType.EDUCATION, 2L, listOf("a".repeat(CONTENT_TARGET_MAX_LENGTH)))
        assertThat(savedLabels()).hasSize(1)
    }

    @Test
    fun `빈 목록이면 지우기만 하고 저장하지 않는다`() {
        service.upsertTargets(ContentType.REVIEW, 3L, emptyList())
        verify(repo).deleteAllByContent(ContentType.REVIEW, 3L)
        verify(repo, never()).saveAll(anyIterable<ContentTarget>())
    }
}
