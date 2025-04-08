package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.utiles.GridDataParser
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

// 단일 격자 셀 구조 (ShortGridCell)
// TMP: 기온, TMX: 최고기온, TMN: 최저기온, VEC: 풍향, WSD: 풍속,
// SKY: 하늘상태, PTY: 강수형태, POP: 강수유무, PCP: 1시간 강수량,
// SNO: 1시간 적설량, REH: 상대습도
data class ShortGridCell(
    val tmp: Double?, // 기온
    val tmx: Double?, // 최고기온
    val tmn: Double?, // 최저기온
    val vec: Double?, // 풍향
    val wsd: Double?, // 풍속
    val sky: Double?, // 하늘상태
    val pty: Double?, // 강수형태
    val pop: Double?, // 강수유무
    val pcp: Double?, // 1시간 강수량
    val sno: Double?, // 1시간 적설량
    val reh: Double?  // 상대습도
)

@Service
class ShortGridForecastService(
    private val weatherData: WeatherData
) {

    /**
     * tmef -> 2차원 ShortGridCell 매핑
     * 여러 tmef에 대한 격자를 저장해둠
     */
    private val shortTMEFGridMap = mutableMapOf<String, MutableList<MutableList<ShortGridCell>>>()
    // ReadWriteLock을 사용해 읽기와 쓰기를 분리
    private val shortReadWriteLock = ReentrantReadWriteLock()

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
            weatherData.fetchShortForecast(tmfc, tmef, "TMP"),
            weatherData.fetchShortForecast(tmfc, tmef, "TMX"),
            weatherData.fetchShortForecast(tmfc, tmef, "TMN"),
            weatherData.fetchShortForecast(tmfc, tmef, "VEC"),
            weatherData.fetchShortForecast(tmfc, tmef, "WSD"),
            weatherData.fetchShortForecast(tmfc, tmef, "SKY"),
            weatherData.fetchShortForecast(tmfc, tmef, "PTY"),
            weatherData.fetchShortForecast(tmfc, tmef, "POP"),
            weatherData.fetchShortForecast(tmfc, tmef, "PCP"),
            weatherData.fetchShortForecast(tmfc, tmef, "SNO"),
            weatherData.fetchShortForecast(tmfc, tmef, "REH")
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
        val monoList = tmefList.map { tmef ->
            fetchShortGrid(tmfc, tmef).map { grid -> Pair(tmef, grid) }
        }
        return Mono.zip(monoList) { arrayOfResults ->
            arrayOfResults.map { it as Pair<String, MutableList<MutableList<ShortGridCell>>> }
        }
    }

    /**
     * 상위 레벨에서 모든 데이터를 수집한 후, 일괄 업데이트하는 메서드
     */
    fun updateAllShortTMEFData(tmfc: String, tmefList: List<String>) {
        fetchShortGrids(tmfc, tmefList)
            .subscribe { listOfGrids ->
                shortReadWriteLock.write {
                    shortTMEFGridMap.clear()
                    listOfGrids.forEach { (tmef, grid) ->
                        shortTMEFGridMap[tmef] = grid
                    }
                }
                println("모든 short tmef 데이터 업데이트 완료. 결과 개수: ${listOfGrids.size}")
            }
    }

    /**
     * shortTMEFGridMap에서, 모든 tmef에 대해 (x, y) 좌표 셀 정보를 조회하여 반환
     * 조회 작업은 readLock을 사용하여 여러 스레드가 동시에 접근 가능하도록 함
     */
    fun getAllShortTMEFDataForCell(x: Int, y: Int): List<ShortForecastResponseDTO> {
        return shortReadWriteLock.read {
            val result = mutableListOf<ShortForecastResponseDTO>()
            for ((tmef, grid) in shortTMEFGridMap) {
                if (y in grid.indices && x in grid[y].indices) {
                    val cell = grid[y][x]
                    result.add(
                        ShortForecastResponseDTO(
                            tmef = tmef,
                            // 필드 매핑은 상황에 맞게 조정 (예시로 TMP를 t1h로 매핑)
                            tmp = cell.tmp,
                            tmx = cell.tmx,
                            tmn = cell.tmn,
                            vec = cell.vec,
                            wsd = cell.wsd,
                            sky = cell.sky,
                            pty = cell.pty,
                            rn1 = cell.pcp,
                            pop = cell.pop,
                            sno = cell.sno,
                            reh = cell.reh
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
                        tmp = tmpGrid[i][j],
                        tmx = tmxGrid[i][j],
                        tmn = tmnGrid[i][j],
                        vec = vecGrid[i][j],
                        wsd = wsdGrid[i][j],
                        sky = skyGrid[i][j],
                        pty = ptyGrid[i][j],
                        pop = popGrid[i][j],
                        pcp = pcpGrid[i][j],
                        sno = snoGrid[i][j],
                        reh = rehGrid[i][j]
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
    private fun printCenterShortGridCell(grid: MutableList<MutableList<ShortGridCell>>) {
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

    /**
     * Iterable 버전의 zip 헬퍼 함수
     * 여러 Mono의 결과를 List로 결합한 후 combinator 함수를 통해 최종 결과를 생성합니다.
     */
    fun <T, R> zipMonos(
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
