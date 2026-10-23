package com.project.byeoldori.forecast.snapshot

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.config.WeatherApiHealthIndicator
import com.project.byeoldori.forecast.service.LiveGridForecastService
import com.project.byeoldori.forecast.service.MidCombinedForecastService
import com.project.byeoldori.forecast.service.ShortGridCell
import com.project.byeoldori.forecast.service.ShortGridCellCodec
import com.project.byeoldori.forecast.service.ShortGridForecastService
import com.project.byeoldori.forecast.service.UltraGridForecastService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.actuate.health.Status
import reactor.core.publisher.Mono
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream

/**
 * 예보 캐시 Redis 스냅샷: 저장·복원 왕복, 발표(tmfc) 기준 유효성, 형식 버전 불일치·Redis 장애 시 기상청 경로로 대체,
 * 복원만으로 warm 헬스가 UP 이 되는지 확인한다.
 */
class ForecastSnapshotTest {

    /** Redis 대신 쓰는 메모리 저장소 */
    class MemoryBlobStore : SnapshotBlobStore {
        val data = HashMap<String, ByteArray>()
        val ttls = HashMap<String, Duration>()
        override fun put(key: String, value: ByteArray, ttl: Duration) { data[key] = value; ttls[key] = ttl }
        override fun get(key: String): ByteArray? = data[key]
    }

    /** Redis 장애 흉내 */
    class BrokenBlobStore : SnapshotBlobStore {
        override fun put(key: String, value: ByteArray, ttl: Duration) = throw IllegalStateException("redis down")
        override fun get(key: String): ByteArray? = throw IllegalStateException("redis down")
    }

    private fun shortCell(i: Int) = ShortGridCell(
        tmp = i - 5, tmx = if (i % 3 == 0) 20 else null, tmn = null, vec = 123.5f, wsd = 1.3f + i,
        sky = i % 4 + 1, pty = 0, pop = 30, pcp = if (i % 2 == 0) 0.1f else null, sno = null, reh = 55
    )

    private fun shortGrid(rows: Int, cols: Int) =
        MutableList(rows) { r -> MutableList(cols) { c -> shortCell(r * cols + c) } }

    // 기상청 격자 응답(149열) 흉내: 2행, 일부 -99(null)
    private val kmaGridBody = (0 until 149 * 2).joinToString(",") { if (it % 10 == 0) "-99.00" else "${it % 30}.0" }

    private fun weatherDataOk(): WeatherData = mock(WeatherData::class.java).also {
        `when`(it.fetchShortForecast(anyString(), anyString(), anyString())).thenReturn(Mono.just(kmaGridBody))
        `when`(it.fetchUltraShortForecast(anyString(), anyString(), anyString())).thenReturn(Mono.just(kmaGridBody))
        `when`(it.fetchLiveWeather(anyString(), anyString())).thenReturn(Mono.just(kmaGridBody))
    }

    @Test
    fun `코덱 왕복 시 정수·실수·null 이 그대로 복원된다`() {
        val slots = linkedMapOf("2026102321" to shortGrid(3, 4), "2026102322" to shortGrid(2, 5))
        val bytes = ForecastSnapshotCodec.encode(ForecastSnapshotKind.SHORT, "2026102320", Instant.EPOCH, slots, ShortGridCellCodec)
        val decoded = ForecastSnapshotCodec.decode(bytes, ForecastSnapshotKind.SHORT, ShortGridCellCodec)!!

        assertThat(decoded.tmfc).isEqualTo("2026102320")
        assertThat(decoded.slots.keys).containsExactly("2026102321", "2026102322")
        assertThat(decoded.slots).isEqualTo(slots)
    }

    @Test
    fun `형식 버전·종류가 다르거나 손상된 스냅샷은 읽지 않고 기상청 경로로 간다`() {
        val blobs = MemoryBlobStore()
        val store = ForecastSnapshotStore(blobs)

        // 다른 SCHEMA_VERSION 으로 쓰인 스냅샷
        val other = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(other)).use {
            it.writeInt(ForecastSnapshotCodec.MAGIC); it.writeInt(ForecastSnapshotCodec.SCHEMA_VERSION + 1)
        }
        blobs.data[ForecastSnapshotKind.SHORT.redisKey] = other.toByteArray()
        assertThat(store.load(ForecastSnapshotKind.SHORT, ShortGridCellCodec)).isNull()

        // 다른 종류(초단기 키에 단기 형식)
        blobs.data[ForecastSnapshotKind.ULTRA.redisKey] = ForecastSnapshotCodec.encode(
            ForecastSnapshotKind.SHORT, "t", Instant.EPOCH, mapOf("a" to shortGrid(1, 1)), ShortGridCellCodec)
        val ultra = UltraGridForecastService(weatherDataOk(), store)
        assertThat(ultra.restoreSnapshot("t")).isEqualTo(SnapshotRestore.NONE)
        assertThat(ultra.getDataCount()).isZero()

        // 손상된 바이트
        blobs.data[ForecastSnapshotKind.SHORT.redisKey] = byteArrayOf(1, 2, 3)
        val short = ShortGridForecastService(weatherDataOk(), store)
        assertThat(short.restoreSnapshot("t")).isEqualTo(SnapshotRestore.NONE)
        assertThat(short.getDataCount()).isZero()
    }

    @Test
    fun `적재에 성공하면 스냅샷을 남기고, 새 컨테이너는 같은 tmfc 면 FRESH 로 바로 올린다`() {
        val blobs = MemoryBlobStore()
        val store = ForecastSnapshotStore(blobs)

        // 이전 컨테이너: 기상청에서 적재 → 스냅샷 저장
        val oldShort = ShortGridForecastService(weatherDataOk(), store)
        oldShort.updateAllShortTMEFData("2026102320", listOf("2026102321", "2026102322")).block()
        val oldUltra = UltraGridForecastService(weatherDataOk(), store)
        oldUltra.updateAllUltraTMEFData("202610232030", listOf("202610232200")).block()
        val oldLive = LiveGridForecastService(weatherDataOk(), store)
        oldLive.updateLiveData("202610232000").block()

        assertThat(blobs.data.keys).containsExactlyInAnyOrder(
            ForecastSnapshotKind.SHORT.redisKey, ForecastSnapshotKind.ULTRA.redisKey, ForecastSnapshotKind.LIVE.redisKey)
        assertThat(blobs.ttls[ForecastSnapshotKind.SHORT.redisKey]).isEqualTo(ForecastSnapshotKind.SHORT.ttl)

        // 새 컨테이너: 기상청은 막혀 있어도(호출하면 실패) 스냅샷으로 올라온다
        val blocked = mock(WeatherData::class.java)
        val newShort = ShortGridForecastService(blocked, store)
        val newUltra = UltraGridForecastService(blocked, store)
        val newLive = LiveGridForecastService(blocked, store)
        assertThat(newShort.restoreSnapshot("2026102320")).isEqualTo(SnapshotRestore.FRESH)
        assertThat(newUltra.restoreSnapshot("202610232030")).isEqualTo(SnapshotRestore.FRESH)
        assertThat(newLive.restoreSnapshot("202610232000")).isEqualTo(SnapshotRestore.FRESH)

        // 공개 응답이 이전 컨테이너와 똑같다
        assertThat(newShort.getAllShortTMEFDataForCell(3, 1)).isEqualTo(oldShort.getAllShortTMEFDataForCell(3, 1))
        assertThat(newUltra.getAllUltraTMEFDataForCell(3, 1)).isEqualTo(oldUltra.getAllUltraTMEFDataForCell(3, 1))
        assertThat(newLive.getLiveDataForCell(3, 1)).isEqualTo(oldLive.getLiveDataForCell(3, 1))
        assertThat(newShort.getAllShortTMEFDataForCell(3, 1)).hasSize(2)

        // warm 헬스: 스냅샷 복원만으로 UP
        val mid = mock(MidCombinedForecastService::class.java)
        assertThat(WeatherApiHealthIndicator(newUltra, newShort, mid).health().status).isEqualTo(Status.UP)
    }

    @Test
    fun `이전 발표 스냅샷은 STALE 로 대체값만 올리고, 메모리에 이미 있으면 덮지 않는다`() {
        val blobs = MemoryBlobStore()
        val store = ForecastSnapshotStore(blobs)
        ShortGridForecastService(weatherDataOk(), store)
            .updateAllShortTMEFData("2026102317", listOf("2026102318")).block()

        val svc = ShortGridForecastService(mock(WeatherData::class.java), store)
        assertThat(svc.restoreSnapshot("2026102320")).isEqualTo(SnapshotRestore.STALE)
        assertThat(svc.getDataCount()).isEqualTo(1)

        // 이미 메모리에 있으면 그대로 둔다
        assertThat(svc.restoreSnapshot("2026102317")).isEqualTo(SnapshotRestore.NONE)
    }

    @Test
    fun `스냅샷이 없으면 NONE`() {
        val svc = LiveGridForecastService(mock(WeatherData::class.java), ForecastSnapshotStore(MemoryBlobStore()))
        assertThat(svc.restoreSnapshot("202610232000")).isEqualTo(SnapshotRestore.NONE)
    }

    @Test
    fun `Redis 장애여도 기상청 적재는 그대로 되고 복원은 NONE 이다`() {
        val store = ForecastSnapshotStore(BrokenBlobStore())
        val svc = ShortGridForecastService(weatherDataOk(), store)

        svc.updateAllShortTMEFData("2026102320", listOf("2026102321")).block()
        assertThat(svc.getDataCount()).isEqualTo(1)

        val fresh = ShortGridForecastService(weatherDataOk(), store)
        assertThat(fresh.restoreSnapshot("2026102320")).isEqualTo(SnapshotRestore.NONE)
        assertThat(fresh.getDataCount()).isZero()
    }
}
