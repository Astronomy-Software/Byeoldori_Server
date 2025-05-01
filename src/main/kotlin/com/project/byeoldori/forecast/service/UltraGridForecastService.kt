package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.utiles.GridDataParser
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// 단일 격자 셀 구조
data class UltraGridCell(
    val t1h: Double?, // 기온
    val vec: Double?, // 풍향
    val wsd: Double?, // 풍속
    val pty: Double?, // 강수형태
    val rn1: Double?, // 1시간 강수량
    val reh: Double?, // 상대습도
    val sky: Double?  // 하늘 상태
)

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
            weatherData.fetchUltraShortForecast(tmfc, tmef, "T1H"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "VEC"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "WSD"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "PTY"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "RN1"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "REH"),
            weatherData.fetchUltraShortForecast(tmfc, tmef, "SKY"),
        ).map { tuple6 ->
            // 응답 데이터 추출
            val t1hData = tuple6.t1
            val vecData = tuple6.t2
            val wsdData = tuple6.t3
            val ptyData = tuple6.t4
            val rn1Data = tuple6.t5
            val rehData = tuple6.t6
            val skyData = tuple6.t7

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
            val result = mutableListOf<UltraForecastResponseDTO>()
            for ((tmef, grid) in ultraTMEFGridMap) {
                if (y in grid.indices && x in grid[y].indices) {
                    val cell = grid[y][x]
                    result.add(
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
                    )
                }
            }
            result.sortBy { it.tmef }
            result
        }
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
                        t1h = t1hGrid[i][j],
                        vec = vecGrid[i][j],
                        wsd = wsdGrid[i][j],
                        pty = ptyGrid[i][j],
                        rn1 = rn1Grid[i][j],
                        reh = rehGrid[i][j],
                        sky = skyGrid[i][j]
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
