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
import java.time.Duration
import java.time.Instant
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
    // 요소별로 비기 시작한 시각(직전 값으로 채운 기간을 로그로 알리기 위함). 갱신 주기는 하나라 동시 갱신은 없다
    private val missingSince = HashMap<String, Instant>()

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
            val grids = linkedMapOf(
                ForecastElement.T1H to GridDataParser.parseGridData(t.t1),
                ForecastElement.VEC to GridDataParser.parseGridData(t.t2),
                ForecastElement.WSD to GridDataParser.parseGridData(t.t3),
                ForecastElement.PTY to GridDataParser.parseGridData(t.t4),
                ForecastElement.RN1 to GridDataParser.parseGridData(t.t5),
                ForecastElement.REH to GridDataParser.parseGridData(t.t6),
                ForecastElement.SKY to GridDataParser.parseGridData(t.t7),
            )
            // 요소 일부만 비었으면(기상청이 그 요소만 안 만든 발표분) 그 요소만 직전 값으로 채우고 나머지는 새 값으로 갱신한다.
            // (2026-11-07~08 SKY 하나가 17시간 비는 동안 기온·바람까지 통째로 멈췄던 일 방지)
            // 직전 격자가 없거나 크기가 다르거나 모두 비었으면 예외 없이 이번 갱신만 건너뛰고 기존 캐시를 유지한다.
            val incomplete = GridDataParser.incompleteGrids(grids)
            trackMissing(incomplete)
            if (incomplete.isNotEmpty()) {
                val filled = fillFromPrevious(grids, incomplete)
                if (filled == null) {
                    logger.warn("실황 격자 중 {} 요소가 비었거나 크기가 달라 이번 갱신을 건너뜁니다. (tmfc={})", incomplete, tmfc)
                    return@map mutableListOf<MutableList<LiveGridCell>>()
                }
                grids.putAll(filled)
                logger.warn("실황 {} 요소가 비어 직전 값으로 채우고 나머지는 갱신합니다. (공백 {}, tmfc={})",
                    incomplete, missingDurations(incomplete), tmfc)
            }
            combineGrids(
                grids.getValue(ForecastElement.T1H),
                grids.getValue(ForecastElement.VEC),
                grids.getValue(ForecastElement.WSD),
                grids.getValue(ForecastElement.PTY),
                grids.getValue(ForecastElement.RN1),
                grids.getValue(ForecastElement.REH),
                grids.getValue(ForecastElement.SKY),
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

    /**
     * 빈 요소를 직전 실황 격자의 같은 요소 값으로 만든 격자로 돌려준다.
     * 정상 요소가 하나도 없거나, 직전 격자가 없거나 크기가 다르면 null(이번 갱신을 건너뜀).
     */
    private fun fillFromPrevious(
        grids: Map<String, MutableList<MutableList<Double?>>>, missing: List<String>
    ): Map<String, MutableList<MutableList<Double?>>>? {
        val reference = grids.filterKeys { it !in missing }.values.firstOrNull() ?: return null
        val rows = reference.size
        val cols = reference.firstOrNull()?.size ?: return null
        val prev = liveGrid
        if (prev.size != rows || prev.any { it.size != cols }) return null
        return missing.associateWith { element ->
            val field = PREVIOUS_FIELD[element] ?: return null
            MutableList(rows) { i -> MutableList(cols) { j -> field(prev[i][j]) } }
        }
    }

    /** 요소별로 비기 시작한 시각을 기록하고, 다시 채워진 요소는 지운다. */
    private fun trackMissing(missing: List<String>, now: Instant = Instant.now()) {
        missingSince.keys.retainAll(missing.toSet())
        missing.forEach { missingSince.putIfAbsent(it, now) }
    }

    private fun missingDurations(missing: List<String>, now: Instant = Instant.now()): String =
        missing.joinToString { el ->
            val minutes = missingSince[el]?.let { Duration.between(it, now).toMinutes() } ?: 0
            "$el ${minutes}분째"
        }

    companion object {
        private const val SNAPSHOT_SLOT = "live"

        // 직전 셀에서 요소 값을 꺼내 격자 값(Double?)으로 되돌린다
        private val PREVIOUS_FIELD: Map<String, (LiveGridCell) -> Double?> = mapOf(
            ForecastElement.T1H to { c -> c.t1h?.toDouble() },
            ForecastElement.VEC to { c -> c.vec?.toDouble() },
            ForecastElement.WSD to { c -> c.wsd?.toDouble() },
            ForecastElement.PTY to { c -> c.pty?.toDouble() },
            ForecastElement.RN1 to { c -> c.rn1?.toDouble() },
            ForecastElement.REH to { c -> c.reh?.toDouble() },
            ForecastElement.SKY to { c -> c.sky?.toDouble() },
        )
    }
}
