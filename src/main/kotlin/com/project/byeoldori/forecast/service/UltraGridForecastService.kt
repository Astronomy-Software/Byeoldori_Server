package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max

// 단일 격자 셀 구조
data class UltraGridCell(
    val t1h: Int?,
    val vec: Int?,
    val wsd: Float?,
    val pty: Int?,
    val rn1: Float?,
    val reh: Int?,
    val sky: Int?
)

{
    // 셀에 유효한 데이터가 있는지 확인하는 헬퍼 함수
    fun hasData(): Boolean {
        return t1h != null || vec != null || wsd != null || pty != null || rn1 != null || reh != null || sky != null
    }
}
@Service
class UltraGridForecastService(
    private val weatherData: WeatherData
) {

    /**
     * tmef -> 2차원 GridCell 매핑
     * 여러 tmef에 대한 격자를 저장해둠
     */
    private val ultraTMEFGridMap = mutableMapOf<String, MutableList<MutableList<UltraGridCell>>>()
    // 전역에 락 객체 정의 (업데이트 시에만 사용)
    private val ultraReadWriteLock = ReentrantReadWriteLock()

    /**
     * (1) 단일 tmef에 대한 초단기예보 격자 데이터 가져오기
     * - 7개 변수(T1H, VEC, WSD, PTY, RN1, REH, SKY)를 병렬 호출 후, 2차원 GridCell로 결합
     * - Mono로 반환하여 비동기 체인에 연결 가능
     */
    fun fetchUltraShortGrid(
        tmfc: String,
        tmef: String
    ): Mono<MutableList<MutableList<UltraGridCell>>> {
        return Mono.zip(
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.T1H),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.VEC),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.WSD),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.PTY),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.RN1),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.REH),
            weatherData.fetchUltraShortForecast(tmfc, tmef, ForecastElement.SKY),
        ).map { tuple7 ->
            // 응답 데이터 추출
            val t1hData = tuple7.t1
            val vecData = tuple7.t2
            val wsdData = tuple7.t3
            val ptyData = tuple7.t4
            val rn1Data = tuple7.t5
            val rehData = tuple7.t6
            val skyData = tuple7.t7

            // 각 데이터 파싱
            val t1hGrid = GridDataParser.parseGridData(t1hData)
            val vecGrid = GridDataParser.parseGridData(vecData)
            val wsdGrid = GridDataParser.parseGridData(wsdData)
            val ptyGrid = GridDataParser.parseGridData(ptyData)
            val rn1Grid = GridDataParser.parseGridData(rn1Data)
            val rehGrid = GridDataParser.parseGridData(rehData)
            val skyGrid = GridDataParser.parseGridData(skyData)

            // 2차원 GridCell 리스트 결합
            val combinedGrid = combineUltraGrids(
                t1hGrid, vecGrid, wsdGrid,
                ptyGrid, rn1Grid, rehGrid, skyGrid
            )

            // (디버깅) 중앙 셀 출력
            printCenterUltraGridCell(combinedGrid)

            // 각 tmef의 결과를 반환 (UltraTMEFGridMap 업데이트는 상위에서 진행)
            combinedGrid
        }
    }

    /**
     * (2) 여러 tmef를 병렬로 처리하여, 각 tmef의 2차원 GridCell 리스트를 모은다.
     * - Mono<List<Pair<tmef, 2차원 GridCell>> 형태로 반환
     */
    fun fetchUltraShortGrids(
        tmfc: String,
        tmefList: List<String>
    ): Mono<List<Pair<String, MutableList<MutableList<UltraGridCell>>>>> {
        val monoList = tmefList.map { tmef ->
            fetchUltraShortGrid(tmfc, tmef).map { grid -> Pair(tmef, grid) }
        }

        return Mono.zip(monoList) { arrayOfResults ->
            arrayOfResults.map { it as Pair<String, MutableList<MutableList<UltraGridCell>>> }
        }
    }

    /**
     * 상위 레벨에서 모든 데이터를 수집한 후, 일괄 업데이트하는 메서드
     */
    fun updateAllUltraTMEFData(tmfc: String, tmefList: List<String>) {
        fetchUltraShortGrids(tmfc, tmefList)
            .subscribe { listOfGrids ->
                ultraReadWriteLock.write {
                    ultraTMEFGridMap.clear()
                    listOfGrids.forEach { (tmef, grid) ->
                        ultraTMEFGridMap[tmef] = grid
                    }
                }
                println("모든 tmef 데이터 업데이트 완료. 결과 개수: ${listOfGrids.size}")
            }
    }



    /**
     * tmef -> GridCell 2차원 리스트 맵에서
     * 모든 tmef에 대해 (x, y) 좌표 셀 정보를 조회하여 반환
     * 조회 작업은 readLock을 사용하여 여러 스레드가 동시에 접근 가능하도록 함
     */
    fun getAllUltraTMEFDataForCell(x: Int, y: Int): List<UltraForecastResponseDTO> {
        return ultraReadWriteLock.read {
            // 가장 가까운 유효 데이터를 찾는 로직 호출
            val nearestCellData = findNearestDataForEachTmef(x, y)

            nearestCellData.map { (tmef, cell) ->
                UltraForecastResponseDTO(
                    tmef = tmef,
                    t1h = cell.t1h,
                    vec = cell.vec,
                    wsd = cell.wsd,
                    pty = cell.pty,
                    rn1 = cell.rn1,
                    reh = cell.reh,
                    sky = cell.sky
                )
            }.sortedBy { it.tmef }
        }
    }

    // 주변 탐색 로직 추가 -> 탐색 반경은 5칸(25km)
    private fun findNearestDataForEachTmef(x: Int, y: Int, maxRadius: Int = 5): List<Pair<String, UltraGridCell>> {
        val results = mutableListOf<Pair<String, UltraGridCell>>()

        // 모든 예보 시간(tmef)에 대해 각각 가장 가까운 데이터를 탐색
        for ((tmef, grid) in ultraTMEFGridMap) {
            if (grid.isEmpty() || grid[0].isEmpty()) continue

            var foundCell: UltraGridCell? = null

            // 1. 요청된 좌표에서 먼저 확인
            if (y in grid.indices && x in grid[y].indices && grid[y][x].hasData()) {
                foundCell = grid[y][x]
            } else {
                // 2. 데이터가 없으면 주변을 나선형으로 탐색
                for (radius in 1..maxRadius) {
                    for (i in -radius..radius) {
                        for (j in -radius..radius) {
                            // 더 안쪽 원은 이미 탐색했으므로 건너뜀
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
    private fun combineUltraGrids(
        t1hGrid: MutableList<MutableList<Double?>>,
        vecGrid: MutableList<MutableList<Double?>>,
        wsdGrid: MutableList<MutableList<Double?>>,
        ptyGrid: MutableList<MutableList<Double?>>,
        rn1Grid: MutableList<MutableList<Double?>>,
        rehGrid: MutableList<MutableList<Double?>>,
        skyGrid: MutableList<MutableList<Double?>>,
    ): MutableList<MutableList<UltraGridCell>> {
        val numRows = t1hGrid.size
        val numCols = if (numRows > 0) t1hGrid[0].size else 0
        val combined = mutableListOf<MutableList<UltraGridCell>>()

        for (i in 0 until numRows) {
            val row = mutableListOf<UltraGridCell>()
            for (j in 0 until numCols) {
                row.add(
                    UltraGridCell(
                        t1h = t1hGrid[i][j]?.toInt(),
                        vec = vecGrid[i][j]?.toInt(),
                        wsd = wsdGrid[i][j]?.toFloat(),
                        pty = ptyGrid[i][j]?.toInt(),
                        rn1 = rn1Grid[i][j]?.toFloat(),
                        reh = rehGrid[i][j]?.toInt(),
                        sky = skyGrid[i][j]?.toInt()
                    )
                )
            }
            combined.add(row)
        }
        return combined
    }

    /**
     * (디버깅용) 중앙 셀 출력
     */
    private fun printCenterUltraGridCell(grid: MutableList<MutableList<UltraGridCell>>) {
        val numRows = grid.size
        val numCols = if (numRows > 0) grid[0].size else 0
        if (numRows == 0 || numCols == 0) {
            println("격자 데이터가 비어있습니다.")
            return
        }
        val centerRow = (numRows - 1) / 2
        val centerCol = (numCols - 1) / 2
        val centerCell = grid[centerRow][centerCol]
        println("중앙 셀 (x: $centerCol , y: $centerRow): $centerCell")
    }
}