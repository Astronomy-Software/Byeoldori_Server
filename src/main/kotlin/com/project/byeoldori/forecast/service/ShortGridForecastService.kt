package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.KmaCallCircuit
import com.project.byeoldori.forecast.api.KmaReachability
import com.project.byeoldori.forecast.api.KmaUnreachableException
import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotKind
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.snapshot.GridCellCodec
import com.project.byeoldori.forecast.snapshot.SnapshotRestore
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import kotlin.math.max

// 단일 격자 셀 구조 (ShortGridCell)
// TMP: 기온, TMX: 최고기온, TMN: 최저기온, VEC: 풍향, WSD: 풍속,
// SKY: 하늘상태, PTY: 강수형태, POP: 강수유무, PCP: 1시간 강수량,
// SNO: 1시간 적설량, REH: 상대습도
data class ShortGridCell(
    val tmp: Int?,
    val tmx: Int?,
    val tmn: Int?,
    val vec: Float?,
    val wsd: Float?,
    val sky: Int?,
    val pty: Int?,
    val pop: Int?,
    val pcp: Float?,
    val sno: Float?,
    val reh: Int?
) {
    // 셀에 유효한 데이터가 있는지 확인하는 헬퍼 함수
    fun hasData(): Boolean {
        return tmp != null || tmx != null || tmn != null || vec != null || wsd != null ||
                sky != null || pty != null || pop != null || pcp != null || sno != null || reh != null
    }
}

// Redis 스냅샷 직렬화용 필드 순서(바꾸면 ForecastSnapshotCodec.SCHEMA_VERSION 을 올린다)
object ShortGridCellCodec : GridCellCodec<ShortGridCell> {
    override val fieldCount = 11
    override fun field(cell: ShortGridCell, index: Int): Float? = when (index) {
        0 -> cell.tmp?.toFloat(); 1 -> cell.tmx?.toFloat(); 2 -> cell.tmn?.toFloat()
        3 -> cell.vec; 4 -> cell.wsd; 5 -> cell.sky?.toFloat(); 6 -> cell.pty?.toFloat()
        7 -> cell.pop?.toFloat(); 8 -> cell.pcp; 9 -> cell.sno; 10 -> cell.reh?.toFloat()
        else -> null
    }
    override fun cell(values: FloatArray) = ShortGridCell(
        tmp = values[0].intOrNull(), tmx = values[1].intOrNull(), tmn = values[2].intOrNull(),
        vec = values[3].orNull(), wsd = values[4].orNull(), sky = values[5].intOrNull(),
        pty = values[6].intOrNull(), pop = values[7].intOrNull(), pcp = values[8].orNull(),
        sno = values[9].orNull(), reh = values[10].intOrNull()
    )
}

@Service
class ShortGridForecastService(
    private val weatherData: WeatherData,
    private val snapshotStore: ForecastSnapshotStore,
    private val reachability: KmaReachability = KmaReachability()
) {

    /**
     * tmef -> 2차원 ShortGridCell 매핑
     * 여러 tmef에 대한 격자를 저장해둠
     */
    // 불변 스냅샷을 @Volatile 참조로 보관 → 갱신 시 새 Map을 만들어 참조만 한 번에 교체(원자적 swap)
    @Volatile
    private var shortTMEFGridMap: Map<String, MutableList<MutableList<ShortGridCell>>> = emptyMap()
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * (1) 단일 tmef에 대한 단기예보 격자 데이터 가져오기
     * - 11개 변수(TMP, TMX, TMN, VEC, WSD, SKY, PTY, POP, PCP, SNO, REH)를 병렬 호출 후,
     *   2차원 ShortGridCell로 결합하여 Mono로 반환함
     *
     * ★ 수정된 부분 ★
     * 기존의 Mono.zip(11개 인자)를 사용하는 대신, 11개의 Mono를 리스트로 묶어 zipMonos 헬퍼를 사용합니다.
     */
    fun fetchShortGrid(
        tmfc: String,
        tmef: String
    ): Mono<MutableList<MutableList<ShortGridCell>>> {
        val monos = listOf(
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.TMP),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.TMX),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.TMN),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.VEC),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.WSD),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.SKY),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.PTY),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.POP),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.PCP),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.SNO),
            weatherData.fetchShortForecast(tmfc, tmef, ForecastElement.REH)
        )
        return zipMonos(monos) { results ->
            // 결과는 List<Any>로 전달되므로, 각 결과를 적절히 캐스팅하여 사용합니다.
            val tmpData = results[0]
            val tmxData = results[1]
            val tmnData = results[2]
            val vecData = results[3]
            val wsdData = results[4]
            val skyData = results[5]
            val ptyData = results[6]
            val popData = results[7]
            val pcpData = results[8]
            val snoData = results[9]
            val rehData = results[10]

            // 각 데이터 파싱 (격자 데이터 파싱)
            val tmpGrid = GridDataParser.parseGridData(tmpData)
            val tmxGrid = GridDataParser.parseGridData(tmxData)
            val tmnGrid = GridDataParser.parseGridData(tmnData)
            val vecGrid = GridDataParser.parseGridData(vecData)
            val wsdGrid = GridDataParser.parseGridData(wsdData)
            val skyGrid = GridDataParser.parseGridData(skyData)
            val ptyGrid = GridDataParser.parseGridData(ptyData)
            val popGrid = GridDataParser.parseGridData(popData)
            val pcpGrid = GridDataParser.parseGridData(pcpData)
            val snoGrid = GridDataParser.parseGridData(snoData)
            val rehGrid = GridDataParser.parseGridData(rehData)

            // 2차원 ShortGridCell 리스트 결합
            val combinedGrid = combineShortGrids(
                tmpGrid, tmxGrid, tmnGrid,
                vecGrid, wsdGrid, skyGrid,
                ptyGrid, popGrid, pcpGrid,
                snoGrid, rehGrid
            )

            // (디버깅) 중앙 셀 출력
            printCenterShortGridCell(combinedGrid)

            combinedGrid
        }
    }

    /**
     * (2) 여러 tmef를 병렬로 처리하여, 각 tmef의 2차원 ShortGridCell 리스트를 모은다.
     * - Mono<List<Pair<tmef, 2차원 ShortGridCell>>> 형태로 반환함
     */
    fun fetchShortGrids(
        tmfc: String,
        tmefList: List<String>
    ): Mono<List<Pair<String, MutableList<MutableList<ShortGridCell>>>>> {
        // 아직 성공한 tmef 가 없는데 연결 단계 실패가 이어지면 남은 tmef 는 호출하지 않고 이번 주기를 중단한다(재시도는 스케줄러가 간격을 늘려서).
        // 하나라도 성공했으면 실패한 tmef 만 건너뛰고 부분 결과를 반영한다.
        val circuit = KmaCallCircuit()
        return Flux.fromIterable(tmefList)
            .flatMap({ tmef ->
                if (circuit.isOpen) return@flatMap Mono.empty<Pair<String, MutableList<MutableList<ShortGridCell>>>>()
                fetchShortGrid(tmfc, tmef)
                    .map { grid -> Pair(tmef, grid) }
                    .doOnNext { circuit.onSuccess(); reachability.onSuccess() }
                    .onErrorResume { e ->
                        circuit.onFailure(e)
                        logger.error("단기 tmef=$tmef 로드 실패, 건너뜀: ${e.message}")
                        Mono.empty()
                    }
            }, 5)  // KMA API 과부하 방지: 최대 5개 tmef 동시 처리
            .collectList()
            .flatMap { list ->
                if (circuit.isOpen) {
                    Mono.error(KmaUnreachableException("단기: 기상청 연결 실패가 이어져 이번 주기 중단 (성공 ${list.size}/${tmefList.size})"))
                } else {
                    Mono.just(list)
                }
            }
    }

    /**
     * 상위 레벨에서 모든 데이터를 수집한 후, 일괄 업데이트하는 메서드
     */
    fun getDataCount(): Int = shortTMEFGridMap.size

    /**
     * 완료를 기다릴 수 있도록 Mono<Void>를 반환한다(호출자가 성공/실패를 관찰·재시도 가능).
     * 갱신은 새 Map을 완성한 뒤 참조를 원자적으로 교체하며, 결과가 비면 기존 캐시를 보존한다.
     */
    fun updateAllShortTMEFData(tmfc: String, tmefList: List<String>): Mono<Void> {
        // 직전 주기가 기상청 연결 실패로 끝났으면 어차피 보낼 첫 요청(첫 tmef 의 TMP) 1건으로 먼저 떠본다
        return reachability.probeThen(
            "단기",
            probe = { weatherData.fetchShortForecast(tmfc, tmefList.first(), ForecastElement.TMP) },
            cycle = { fetchShortGrids(tmfc, tmefList) }
        )
            .flatMap { listOfGrids ->
                if (listOfGrids.isEmpty()) {
                    logger.warn("단기 전체 데이터가 비어 있어 기존 캐시를 유지합니다.")
                    return@flatMap Mono.empty<Void>()
                }
                val newMap = LinkedHashMap<String, MutableList<MutableList<ShortGridCell>>>(listOfGrids.size)
                listOfGrids.forEach { (tmef, grid) -> newMap[tmef] = grid }
                shortTMEFGridMap = newMap  // 원자적 참조 교체
                logger.info("모든 short tmef 데이터 업데이트 완료. 결과 개수: ${listOfGrids.size}")
                // 다음 배포의 새 컨테이너가 기상청 재적재 없이 올릴 수 있게 Redis 에 남긴다
                // 일부 tmef 가 빠졌으면 complete=false 로 남겨, 다음 기동에서 같은 발표여도 기상청에서 다시 받게 한다
                val complete = newMap.size >= tmefList.distinct().size
                snapshotStore.saveAsync(ForecastSnapshotKind.SHORT, tmfc, newMap, ShortGridCellCodec, complete)
            }
            .doOnError { e ->
                reachability.onCycleError(e)
                logger.error("단기 전체 데이터 업데이트 실패", e)
            }
            .then()
    }

    /**
     * 기동 시 Redis 스냅샷을 메모리에 올린다(메모리가 비어 있을 때만).
     * 같은 발표(tmfc)이고 모든 tmef 를 담았으면 FRESH(기상청 재적재 생략),
     * 이전 발표이거나 일부 tmef 만 담긴 스냅샷이면 STALE(대체값으로 두고 기상청에서 새로 받음).
     */
    fun restoreSnapshot(expectedTmfc: String): SnapshotRestore {
        if (shortTMEFGridMap.isNotEmpty()) return SnapshotRestore.NONE
        val snapshot = snapshotStore.load(ForecastSnapshotKind.SHORT, ShortGridCellCodec) ?: return SnapshotRestore.NONE
        if (snapshot.slots.isEmpty()) return SnapshotRestore.NONE
        shortTMEFGridMap = snapshot.slots
        val result = if (snapshot.tmfc == expectedTmfc && snapshot.complete) SnapshotRestore.FRESH else SnapshotRestore.STALE
        logger.info("[스냅샷] 단기 복원 {} (tmfc={}, 기대 tmfc={}, slot={}, complete={}, 저장 {})",
            result, snapshot.tmfc, expectedTmfc, snapshot.slots.size, snapshot.complete, snapshot.savedAt)
        return result
    }

    /**
     * shortTMEFGridMap에서, 모든 tmef에 대해 (x, y) 좌표 셀 정보를 조회하여 반환
     * 조회 작업은 readLock을 사용하여 여러 스레드가 동시에 접근 가능하도록 함
     */
    fun getAllShortTMEFDataForCell(x: Int, y: Int): List<ShortForecastResponseDTO> {
        // 가장 가까운 유효 데이터를 찾는 로직 호출
        val nearestCellData = findNearestDataForEachTmef(x, y)

        return nearestCellData.map { (tmef, cell) ->
            ShortForecastResponseDTO(
                // 내부 키는 기상청 호출용 10자리(yyyyMMddHH). 응답은 초단기와 같은 12자리(yyyyMMddHHmm)로 맞춘다.
                tmef = if (tmef.length == 10) "${tmef}00" else tmef,
                tmp = cell.tmp,
                tmx = cell.tmx,
                tmn = cell.tmn,
                vec = cell.vec,
                wsd = cell.wsd,
                sky = cell.sky,
                pty = cell.pty,
                pcp = cell.pcp,
                pop = cell.pop,
                sno = cell.sno,
                reh = cell.reh
            )
        }.sortedBy { it.tmef }
    }

    private fun findNearestDataForEachTmef(x: Int, y: Int, maxRadius: Int = 5): List<Pair<String, ShortGridCell>> {
        val snapshot = shortTMEFGridMap  // 현재 스냅샷을 한 번만 읽어 일관성 보장
        val results = mutableListOf<Pair<String, ShortGridCell>>()

        for ((tmef, grid) in snapshot) {
            if (grid.isEmpty() || grid[0].isEmpty()) continue

            var foundCell: ShortGridCell? = null

            if (y in grid.indices && x in grid[y].indices && grid[y][x].hasData()) {
                foundCell = grid[y][x]
            } else {
                for (radius in 1..maxRadius) {
                    for (i in -radius..radius) {
                        for (j in -radius..radius) {
                            if (max(kotlin.math.abs(i), kotlin.math.abs(j)) < radius) continue

                            val newX = x + j
                            val newY = y + i

                            if (newY in grid.indices && newX in grid[newY].indices && grid[newY][newX].hasData()) {
                                foundCell = grid[newY][newX]
                                break
                            }
                        }
                        if (foundCell != null) break
                    }
                    if (foundCell != null) break
                }
            }
            if (foundCell != null) {
                results.add(tmef to foundCell)
            }
        }
        return results
    }

    /**
     * 격자 결합 로직
     */
    private fun combineShortGrids(
        tmpGrid: MutableList<MutableList<Double?>>,
        tmxGrid: MutableList<MutableList<Double?>>,
        tmnGrid: MutableList<MutableList<Double?>>,
        vecGrid: MutableList<MutableList<Double?>>,
        wsdGrid: MutableList<MutableList<Double?>>,
        skyGrid: MutableList<MutableList<Double?>>,
        ptyGrid: MutableList<MutableList<Double?>>,
        popGrid: MutableList<MutableList<Double?>>,
        pcpGrid: MutableList<MutableList<Double?>>,
        snoGrid: MutableList<MutableList<Double?>>,
        rehGrid: MutableList<MutableList<Double?>>
    ): MutableList<MutableList<ShortGridCell>> {
        val numRows = tmpGrid.size
        val numCols = if (numRows > 0) tmpGrid[0].size else 0
        val combined = mutableListOf<MutableList<ShortGridCell>>()
        for (i in 0 until numRows) {
            val row = mutableListOf<ShortGridCell>()
            for (j in 0 until numCols) {
                row.add(
                    ShortGridCell(
                        tmp = tmpGrid[i][j]?.toInt(),
                        tmx = tmxGrid[i][j]?.toInt(),
                        tmn = tmnGrid[i][j]?.toInt(),
                        vec = vecGrid[i][j]?.toFloat(),
                        wsd = wsdGrid[i][j]?.toFloat(),
                        sky = skyGrid[i][j]?.toInt(),
                        pty = ptyGrid[i][j]?.toInt(),
                        pop = popGrid[i][j]?.toInt(),
                        pcp = pcpGrid[i][j]?.toFloat(),
                        sno = snoGrid[i][j]?.toFloat(),
                        reh = rehGrid[i][j]?.toInt()
                    )
                )
            }
            combined.add(row)
        }
        return combined
    }

    private fun printCenterShortGridCell(grid: MutableList<MutableList<ShortGridCell>>) {
        if (!logger.isDebugEnabled) return
        val numRows = grid.size
        val numCols = if (numRows > 0) grid[0].size else 0
        if (numRows == 0 || numCols == 0) {
            logger.debug("격자 데이터가 비어있습니다.")
            return
        }
        val centerRow = (numRows - 1) / 2
        val centerCol = (numCols - 1) / 2
        logger.debug("중앙 셀 (x: $centerCol , y: $centerRow): ${grid[centerRow][centerCol]}")
    }

    /**
     * Iterable 버전의 zip 헬퍼 함수
     * 여러 Mono의 결과를 List로 결합한 후 combinator 함수를 통해 최종 결과를 생성합니다.
     */
    private fun <T, R> zipMonos(
        monos: Iterable<Mono<T>>,
        combinator: (List<T>) -> R
    ): Mono<R> {
        return Mono.zip(monos) { results ->
            // 결과는 Array<Any?> 타입이므로, 안전하게 캐스팅 후 List로 변환합니다.
            val list = results.map { it as T }
            combinator(list)
        }
    }
}