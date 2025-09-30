package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.UltraForecastResponseDTO
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class UltraGridCell(
    val t1h: Int?, val vec: Int?, val wsd: Float?,
    val pty: Int?, val rn1: Float?, val reh: Int?, val sky: Int?
)

@Service
class UltraGridForecastService(
    private val weatherData: WeatherData
) {
    private val ultraTMEFGridMap = mutableMapOf<String, MutableList<MutableList<UltraGridCell>>>()
    private val ultraReadWriteLock = ReentrantReadWriteLock()

    /**
     * 최종적으로 사용자에게 초단기 예보 데이터를 제공하는 함수.
     * 캐시를 확인하고, 없으면 실시간으로 데이터를 가져와 응답을 완성합니다.
     */
    fun getAllUltraTMEFDataForCell(x: Int, y: Int): List<UltraForecastResponseDTO> {
        val forecastTimes = ForecastTimeUtil.getNext6UltraTmef()

        return forecastTimes.map { tmef ->
            val gridForTime = getGridForTMEF(tmef)
            val cellData = gridForTime?.getOrNull(y)?.getOrNull(x)

            UltraForecastResponseDTO(
                tmef = tmef,
                t1h = cellData?.t1h, vec = cellData?.vec, wsd = cellData?.wsd,
                pty = cellData?.pty, rn1 = cellData?.rn1, reh = cellData?.reh,
                sky = cellData?.sky
            )
        }
    }

    /**
     * 특정 예보 시간(tmef)의 격자 데이터를 가져오는 헬퍼 함수.
     * 캐시를 먼저 읽고, 없으면 쓰기 락을 걸어 실시간 API를 호출합니다.
     */
    private fun getGridForTMEF(tmef: String): MutableList<MutableList<UltraGridCell>>? {
        ultraReadWriteLock.read {
            if (ultraTMEFGridMap.containsKey(tmef)) return ultraTMEFGridMap[tmef]
        }
        return ultraReadWriteLock.write {
            if (ultraTMEFGridMap.containsKey(tmef)) return@write ultraTMEFGridMap[tmef]

            val fetchedGrid = fetchAndParseRealtime(tmef)
            if (fetchedGrid != null) {
                ultraTMEFGridMap[tmef] = fetchedGrid
            }
            fetchedGrid
        }
    }

    /**
     * 스케줄러가 백그라운드에서 캐시를 채우기 위해 호출하는 함수.
     */
    fun updateAllUltraTMEFData(tmfc: String, tmefList: List<String>) {
        val monoList = tmefList.map { tmef -> fetchUltraGrid(tmfc, tmef).map { tmef to it } }
        Mono.zip(monoList) { results ->
            results.map { it as Pair<String, MutableList<MutableList<UltraGridCell>>> }
        }.subscribe { listOfGrids ->
            ultraReadWriteLock.write {
                ultraTMEFGridMap.clear()
                listOfGrids.forEach { (tmef, grid) -> ultraTMEFGridMap[tmef] = grid }
            }
        }
    }

    /**
     * 실시간으로 API를 호출하여 데이터를 가져오는 함수.
     */
    private fun fetchAndParseRealtime(tmef: String): MutableList<MutableList<UltraGridCell>>? {
        val tmfc = ForecastTimeUtil.getStableUltraTmfc()
        return fetchUltraGrid(tmfc, tmef).block()
    }

    /**
     * 단일 tmef에 대한 초단기예보 격자 데이터를 API로부터 가져와 파싱하는 공통 로직.
     */
    private fun fetchUltraGrid(tmfc: String, tmef: String): Mono<MutableList<MutableList<UltraGridCell>>> {
        val elements = listOf(
            ForecastElement.T1H, ForecastElement.VEC, ForecastElement.WSD, ForecastElement.PTY,
            ForecastElement.RN1, ForecastElement.REH, ForecastElement.SKY
        )
        return Flux.fromIterable(elements)
            .flatMap({ element ->
                weatherData.fetchUltraShortForecast(tmfc, tmef, element)
                    .map { response -> element to GridDataParser.parseGridData(response) }
            }, 3)
            .collectMap({ it.first }, { it.second })
            .map { gridMap -> combineUltraGrids(
                gridMap[ForecastElement.T1H]!!, gridMap[ForecastElement.VEC]!!, gridMap[ForecastElement.WSD]!!,
                gridMap[ForecastElement.PTY]!!, gridMap[ForecastElement.RN1]!!, gridMap[ForecastElement.REH]!!,
                gridMap[ForecastElement.SKY]!!
            )}
    }

    /**
     * 각 요소별 격자 데이터를 하나의 UltraGridCell 격자로 합치는 함수.
     */
    private fun combineUltraGrids(
        t1hGrid: MutableList<MutableList<Double?>>, vecGrid: MutableList<MutableList<Double?>>,
        wsdGrid: MutableList<MutableList<Double?>>, ptyGrid: MutableList<MutableList<Double?>>,
        rn1Grid: MutableList<MutableList<Double?>>, rehGrid: MutableList<MutableList<Double?>>,
        skyGrid: MutableList<MutableList<Double?>>
    ): MutableList<MutableList<UltraGridCell>> {
        val numRows = t1hGrid.size
        val numCols = if (numRows > 0) t1hGrid[0].size else 0
        val combined = mutableListOf<MutableList<UltraGridCell>>()
        for (i in 0 until numRows) {
            val row = mutableListOf<UltraGridCell>()
            for (j in 0 until numCols) {
                row.add(UltraGridCell(
                    t1h = t1hGrid[i][j]?.toInt(), vec = vecGrid[i][j]?.toInt(), wsd = wsdGrid[i][j]?.toFloat(),
                    pty = ptyGrid[i][j]?.toInt(), rn1 = rn1Grid[i][j]?.toFloat(), reh = rehGrid[i][j]?.toInt(),
                    sky = skyGrid[i][j]?.toInt()
                ))
            }
            combined.add(row)
        }
        return combined
    }
}