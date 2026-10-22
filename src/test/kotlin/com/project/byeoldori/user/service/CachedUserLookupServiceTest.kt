package com.project.byeoldori.user.service

import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

/** JWT 필터가 요청마다 부르는 사용자 조회가 email 조회 한 번으로 끝나는지 확인한다. */
class CachedUserLookupServiceTest {

    private val repo = mock(UserRepository::class.java)
    private val service = CachedUserLookupService(repo)

    @Test
    fun `email 로 한 번만 조회하고 PK 재조회는 하지 않는다`() {
        val u = User(id = 7, email = "star@test.com", passwordHash = "x", name = "별", phone = "010", nickname = "별지기")
        `when`(repo.findByEmail("star@test.com")).thenReturn(Optional.of(u))

        assertThat(service.findByEmail("star@test.com")).isSameAs(u)
        verify(repo, never()).findById(anyLong())
    }

    @Test
    fun `없는 email 은 null`() {
        `when`(repo.findByEmail("none@test.com")).thenReturn(Optional.empty())
        assertThat(service.findByEmail("none@test.com")).isNull()
    }
}
