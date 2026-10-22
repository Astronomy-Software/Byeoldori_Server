package com.project.byeoldori.observationsites

import com.project.byeoldori.observationsites.controller.ObservationSiteController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/** 관측지 목록의 정렬 키 화이트리스트와 size 상한 (Spring 컨텍스트 불필요). */
class ObservationSitePagingTest {

    @Test
    fun `엔티티에 없는 정렬 키는 버리고 name 정렬로 대신한다`() {
        val p = ObservationSiteController.sanitize(PageRequest.of(2, 20, Sort.by("password", "foo")))
        assertThat(p.pageNumber).isEqualTo(2)
        assertThat(p.sort).isEqualTo(Sort.by("name"))
    }

    @Test
    fun `허용한 정렬 키와 방향은 그대로 둔다`() {
        val p = ObservationSiteController.sanitize(
            PageRequest.of(0, 20, Sort.by(Sort.Order.desc("id"), Sort.Order.asc("foo")))
        )
        assertThat(p.sort).isEqualTo(Sort.by(Sort.Order.desc("id")))
    }

    @Test
    fun `size 는 100 을 넘지 않는다`() {
        assertThat(ObservationSiteController.sanitize(PageRequest.of(0, 2000)).pageSize).isEqualTo(100)
        assertThat(ObservationSiteController.sanitize(PageRequest.of(0, 100)).pageSize).isEqualTo(100)
    }
}
