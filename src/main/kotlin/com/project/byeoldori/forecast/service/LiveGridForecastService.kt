package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.KmaReachability
import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.LiveForecastResponseDTO
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotKind
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.snapshot.GridCellCodec
import com.project.byeoldori.forecast.snapshot.SnapshotRestore
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.math.abs

data class LiveGridCell(
    val t1h: Int?,
    val vec: Int?,
    val wsd: Float?,
    val pty: Int?,
    val rn1: Float?,
    val reh: Int?,
    val sky: Int?
) {
    fun hasData(): Boolean =
        t1h != null || vec != null || wsd != null || pty != null || rn1 != null || reh != null || sky != null
}

// Redis 스냅샷 직렬화용 필드 순서(바꾸면 ForecastSnapshotCodec.SCHEMA_VERSION 을 올린다)
object LiveGridCellCodec : GridCellCodec<LiveGridCell> {
    override val fieldCount = 7
    override fun field(cell: LiveGridCell, index: Int): Float? = when (index) {
        0 -> cell.t1h?.toFloat(); 1 -> cell.vec?.toFloat(); 2 -> cell.wsd; 3 -> cell.pty?.toFloat()
        4 -> cell.rn1; 5 -> cell.reh?.toFloat(); 6 -> cell.sky?.toFloat()
        else -> null
    }
    override fun cell(values: FloatArray) = LiveGridCell(
        t1h = values[0].intOrNull(), vec = values[1].intOrNull(), wsd = values[2].orNull(),
        pty = values[3].intOrNull(), rn1 = values[4].orNull(), reh = values[5].intOrNull(),
        sky = values[6].intOrNull()
    )
}

@Service
class LiveGridForecastService(
    private val weatherData: WeatherData,
    private val snapshotStore: ForecastSnapshotStore,
    private val reachability: KmaReachability = KmaReachability()
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    // 갱신 시 새 격자를 완성한 뒤 참조를 원자적으로 교체(@Volatile). 읽기는 현재 참조를 그대로 사용.
    @Volatile
    private var liveGrid: MutableList<MutableList<LiveGridCell>> = mutableListOf()

    /**
     * 완료를 기다릴 수 있도록 Mono<Void>를 반환한다(호출자가 성공/실패를 관찰·재시도 가능).
     * 결과가 비면 기존 캐시를 보존한다.
     */
    fun updateLiveData(tmfc: String): Mono<Void> {
        // 직전 주기가 기상청 연결 실패로 끝났으면 어차피 보낼 첫 요청(T1H) 1건으로 먼저 떠본다
        return reachability.probeThen(
            "실황",
            probe = { weatherData.fetchLiveWeather(tmfc, ForecastElement.T1H) },
            cycle = { fetchLiveGrid(tmfc) }
        ).flatMap { grid ->
            if (grid.isEmpty() || grid[0].isEmpty()) {
                logger.warn("실황 격자 데이터가 비어 있어 기존 캐시를 유지합니다. (tmfc=$tmfc)")
                return@flatMap Mono.empty<Void>()
            }
            liveGrid = grid  // 원자적 참조 교체
            logger.info("실황 격자 데이터 업데이트 완료 (tmfc=$tmfc)")
            // 다음 배포의 새 컨테이너가 기상청 재적재 없이 올릴 수 있게 Redis 에 남긴다
            snapshotStore.saveAsync(ForecastSnapshotKind.LIVE, tmfc, mapOf(SNAPSHOT_SLOT to grid), LiveGridCellCodec)
        }.doOnError { e ->
            reachability.onCycleError(e)
            logger.error("실황 격자 데이터 업데이트 실패", e)
        }.then()
    }

    private fun fetchLiveGrid(tmfc: String): Mono<MutableList<MutableList<LiveGridCell>>> {
        return Mono.zip(
            weatherData.fetchLiveWeather(tmfc, ForecastElement.T1H),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.VEC),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.WSD),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.PTY),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.RN1),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.REH),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.SKY),
        ).map { t ->
            reachability.onSuccess()
            combineGrids(
                GridDataParser.parseGridData(t.t1),
                GridDataParser.parseGridData(t.t2),
                GridDataParser.parseGridData(t.t3),
                GridDataParser.parseGridData(t.t4),
                GridDataParser.parseGridData(t.t5),
                GridDataParser.parseGridData(t.t6),
                GridDataParser.parseGridData(t.t7),
            )
        }
    }

    /**
     * 기동 시 Redis 스냅샷을 메모리에 올린다(메모리가 비어 있을 때만).
     * 같은 발표(tmfc)면 FRESH(기상청 재적재 생략), 이전 발표면 STALE(대체값으로 두고 기상청에서 새로 받음).
     */
    fun restoreSnapshot(expectedTmfc: String): SnapshotRestore {
        if (liveGrid.isNotEmpty()) return SnapshotRestore.NONE
        val snapshot = snapshotStore.load(ForecastSnapshotKind.LIVE, LiveGridCellCodec) ?: return SnapshotRestore.NONE
        val grid = snapshot.slots[SNAPSHOT_SLOT]
        if (grid.isNullOrEmpty() || grid[0].isEmpty()) return SnapshotRestore.NONE
        liveGrid = grid
        val result = if (snapshot.tmfc == expectedTmfc) SnapshotRestore.FRESH else SnapshotRestore.STALE
        logger.info("[스냅샷] 실황 복원 {} (tmfc={}, 기대 tmfc={}, 저장 {})", result, snapshot.tmfc, expectedTmfc, snapshot.savedAt)
        return result
    }

    fun getLiveDataForCell(x: Int, y: Int): LiveForecastResponseDTO? {
        val cell = findNearest(x, y) ?: return null
        return LiveForecastResponseDTO(
            t1h = cell.t1h, vec = cell.vec, wsd = cell.wsd,
            pty = cell.pty, rn1 = cell.rn1, reh = cell.reh, sky = cell.sky
        )
    }

    private fun findNearest(x: Int, y: Int, maxRadius: Int = 5): LiveGridCell? {
        val grid = liveGrid
        if (grid.isEmpty() || grid[0].isEmpty()) return null
        if (y in grid.indices && x in grid[y].indices && grid[y][x].hasData()) return grid[y][x]
        for (radius in 1..maxRadius) {
            for (i in -radius..radius) {
                for (j in -radius..radius) {
                    if (maxOf(abs(i), abs(j)) < radius) continue
                    val ny = y + i; val nx = x + j
                    if (ny in grid.indices && nx in grid[ny].indices && grid[ny][nx].hasData()) return grid[ny][nx]
                }
            }
        }
        return null
    }

    private fun combineGrids(
        t1h: MutableList<MutableList<Double?>>, vec: MutableList<MutableList<Double?>>,
        wsd: MutableList<MutableList<Double?>>, pty: MutableList<MutableList<Double?>>,
        rn1: MutableList<MutableList<Double?>>, reh: MutableList<MutableList<Double?>>,
        sky: MutableList<MutableList<Double?>>
    ): MutableList<MutableList<LiveGridCell>> {
        val rows = t1h.size
        val cols = if (rows > 0) t1h[0].size else 0
        return MutableList(rows) { i ->
            MutableList(cols) { j ->
                LiveGridCell(
                    t1h = t1h[i][j]?.toInt(), vec = vec[i][j]?.toInt(), wsd = wsd[i][j]?.toFloat(),
                    pty = pty[i][j]?.toInt(), rn1 = rn1[i][j]?.toFloat(), reh = reh[i][j]?.toInt(),
                    sky = sky[i][j]?.toInt()
                )
            }
        }
    }

    companion object {
        private const val SNAPSHOT_SLOT = "live"
    }
}
