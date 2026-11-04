package com.project.byeoldori.community.common.dto

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

class PageResponseTest {

    @Test
    fun `Spring Page 의 번호·크기·총건수·총페이지를 옮긴다`() {
        val page = PageImpl(listOf("a", "b", "c"), PageRequest.of(2, 5), 23)
        val res = page.toPageResponse()

        assertThat(res.content).containsExactly("a", "b", "c")
        assertThat(res.page).isEqualTo(2)
        assertThat(res.size).isEqualTo(5)
        assertThat(res.totalElements).isEqualTo(23)
        assertThat(res.totalPages).isEqualTo(5) // ceil(23/5)
    }

    @Test
    fun `마지막 페이지가 꽉 차면 총페이지는 나눗셈 그대로`() {
        val res = PageImpl(listOf(1, 2), PageRequest.of(0, 2), 4).toPageResponse()
        assertThat(res.totalPages).isEqualTo(2)
    }

    @Test
    fun `빈 페이지`() {
        val res = PageImpl(emptyList<Int>(), PageRequest.of(0, 10), 0).toPageResponse()
        assertThat(res.content).isEmpty()
        assertThat(res.totalElements).isEqualTo(0)
        assertThat(res.totalPages).isEqualTo(0)
        assertThat(res.size).isEqualTo(10)
    }

    @Test
    fun `unpaged 는 page 0·size 는 내용 수`() {
        val res = PageImpl(listOf(1, 2, 3), Pageable.unpaged(), 3).toPageResponse()
        assertThat(res.page).isEqualTo(0)
        assertThat(res.size).isEqualTo(3)
        assertThat(res.totalPages).isEqualTo(1)
    }
}
