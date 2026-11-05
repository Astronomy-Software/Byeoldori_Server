package com.project.byeoldori.admin.service

import com.project.byeoldori.admin.domain.SystemConfig
import com.project.byeoldori.admin.repository.SystemConfigRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional

/**
 * 키-값 시스템 설정. 불리언 해석(엄격 파싱·기본값)과 upsert 분기를 확인한다.
 */
class SystemConfigServiceTest {

    private val repo = mock(SystemConfigRepository::class.java)
    private val service = SystemConfigService(repo)

    private fun stored(key: String, value: String) {
        `when`(repo.findById(key)).thenReturn(Optional.of(SystemConfig(key = key, value = value)))
    }

    @Test
    fun `설정이 없으면 기본값`() {
        `when`(repo.findById("x")).thenReturn(Optional.empty())
        assertThat(service.getBool("x", true)).isTrue()
        assertThat(service.getBool("x", false)).isFalse()
    }

    @Test
    fun `true·false 는 공백을 무시하고 해석한다`() {
        stored("a", "true"); assertThat(service.getBool("a", false)).isTrue()
        stored("b", "  false  "); assertThat(service.getBool("b", true)).isFalse()
        stored("c", "\ttrue\n"); assertThat(service.getBool("c", false)).isTrue()
    }

    @Test
    fun `엄격 파싱 - 대소문자 변형·yes·1 은 기본값으로 떨어진다`() {
        for (v in listOf("TRUE", "True", "yes", "1", "on", "", "false ✓")) {
            stored("k", v)
            assertThat(service.getBool("k", true)).`as`("'%s' default=true", v).isTrue()
            assertThat(service.getBool("k", false)).`as`("'%s' default=false", v).isFalse()
        }
    }

    @Test
    fun `검수 필요 여부는 미설정 시 켜져 있다`() {
        `when`(repo.findById(SystemConfigService.MODERATION_REQUIRED)).thenReturn(Optional.empty())
        assertThat(service.moderationRequired()).isTrue()

        stored(SystemConfigService.MODERATION_REQUIRED, "false")
        assertThat(service.moderationRequired()).isFalse()
    }

    @Test
    fun `기존 키는 값만 바꾸고 save 를 호출하지 않는다(영속 상태 변경)`() {
        val existing = SystemConfig(key = "k", value = "old")
        `when`(repo.findById("k")).thenReturn(Optional.of(existing))

        service.set("k", "new")

        assertThat(existing.value).isEqualTo("new")
        verify(repo, never()).save(any(SystemConfig::class.java))
    }

    @Test
    fun `새 키는 save 로 만든다`() {
        `when`(repo.findById("fresh")).thenReturn(Optional.empty())

        service.set("fresh", "v")

        val captor = ArgumentCaptor.forClass(SystemConfig::class.java)
        verify(repo).save(captor.capture())
        assertThat(captor.value.key).isEqualTo("fresh")
        assertThat(captor.value.value).isEqualTo("v")
    }

    @Test
    fun `전체 조회는 key→value 맵`() {
        `when`(repo.findAll()).thenReturn(listOf(SystemConfig("a", "1"), SystemConfig("b", "2")))
        assertThat(service.getAll()).containsExactlyInAnyOrderEntriesOf(mapOf("a" to "1", "b" to "2"))
    }
}
