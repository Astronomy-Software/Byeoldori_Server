package com.project.byeoldori.observationsites.init

import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 시드는 빈 DB 에서만 넣는다 — 관리자가 지우거나 고친 관측지가 배포(재기동) 때마다 되살아나지 않도록.
 */
class ObservationSiteSeederTest {

    private val repo = mock(ObservationSiteRepository::class.java)
    private val seeder = ObservationSiteSeeder(repo)

    @Test
    fun `관측지가 이미 있으면 아무것도 넣지 않는다`() {
        `when`(repo.count()).thenReturn(14L)
        seeder.run()
        verify(repo, never()).saveAll(anyList())
    }

    @Test
    fun `빈 DB 면 샘플 관측지를 넣는다`() {
        `when`(repo.count()).thenReturn(0L)
        seeder.run()
        verify(repo).saveAll(anyList())
    }
}
