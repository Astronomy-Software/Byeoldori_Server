package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.ShortForecastResponseDTO
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.ForecastTimeUtil
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class ShortGridCell(
    val tmp: Int?, val tmx: Int?, val tmn: Int?, val vec: Float?, val wsd: Float?,
    val sky: Int?, val pty: Int?, val pop: Int?, val pcp: Float?,
    val sno: Float?, val reh: Int?
)

@Service
class ShortGridForecastService(
    private val weatherData: WeatherData
) {
    private val shortTMEFGridMap = mutableMapOf<String, MutableList<MutableList<ShortGridCell>>>()
    private val shortReadWriteLock = ReentrantReadWriteLock()

    /**
     * 최종적으로 사용자에게 단기 예보 데이터를 제공하는 함수.
     * 캐시를 확인하고, 없으면 실시간으로 데이터를 가져와 응답을 완성합니다.
     */
    fun getAllShortTMEFDataForCell(x: Int, y: Int): List<ShortForecastResponseDTO> {
        val forecastTimes = ForecastTimeUtil.getNext24ShortTmef()

        return forecastTimes.map { tmef ->
            val gridForTime = getGridForTMEF(tmef)
            val cellData = gridForTime?.getOrNull(y)?.getOrNull(x)
            ShortForecastResponseDTO(
                tmef = tmef, tmp = cellData?.tmp, tmx = cellData?.tmx, tmn = cellData?.tmn,
                vec = cellData?.vec, wsd = cellData?.wsd, sky = cellData?.sky, pty = cellData?.pty,
                pop = cellData?.pop, pcp = cellData?.pcp, sno = cellData?.sno, reh = cellData?.reh
            )
        }
    }

    /**
     * 특정 예보 시간(tmef)의 격자 데이터를 가져오는 헬퍼 함수.
     * 캐시를 먼저 읽고, 없으면 쓰기 락을 걸어 실시간 API를 호출합니다.
     */
    private fun getGridForTMEF(tmef: String): MutableList<MutableList<ShortGridCell>>? {
        shortReadWriteLock.read {
            if (shortTMEFGridMap.containsKey(tmef)) return shortTMEFGridMap[tmef]
        }
        return shortReadWriteLock.write {
            if (shortTMEFGridMap.containsKey(tmef)) return@write shortTMEFGridMap[tmef]

            val fetchedGrid = fetchAndParseRealtime(tmef)
            if (fetchedGrid != null) {
                shortTMEFGridMap[tmef] = fetchedGrid
            }
            fetchedGrid
        }
    }

    /**
     * 스케줄러가 백그라운드에서 캐시를 채우기 위해 호출하는 함수.
     */
    fun updateAllShortTMEFData(tmfc: String, tmefList: List<String>) {
        val monoList = tmefList.map { tmef -> fetchShortGrid(tmfc, tmef).map { tmef to it } }
        Mono.zip(monoList) { results ->
            results.map { it as Pair<String, MutableList<MutableList<ShortGridCell>>> }
        }.subscribe { listOfGrids ->
            shortReadWriteLock.write {
                shortTMEFGridMap.clear()
                listOfGrids.forEach { (tmef, grid) -> shortTMEFGridMap[tmef] = grid }
            }
        }
    }

    /**
     * 실시간으로 API를 호출하여 데이터를 가져오는 함수.
     */
    private fun fetchAndParseRealtime(tmef: String): MutableList<MutableList<ShortGridCell>>? {
        val tmfc = ForecastTimeUtil.getStableShortTmfc()
        return fetchShortGrid(tmfc, tmef).block()
    }

    /**
     * 단일 tmef에 대한 단기예보 격자 데이터를 API로부터 가져와 파싱하는 공통 로직.
     */
    private fun fetchShortGrid(tmfc: String, tmef: String): Mono<MutableList<MutableList<ShortGridCell>>> {
        val elements = listOf(
            ForecastElement.TMP, ForecastElement.TMX, ForecastElement.TMN, ForecastElement.VEC, ForecastElement.WSD,
            ForecastElement.SKY, ForecastElement.PTY, ForecastElement.POP, ForecastElement.PCP,
            ForecastElement.SNO, ForecastElement.REH
        )
        return Flux.fromIterable(elements)
            .flatMap({ element ->
                weatherData.fetchShortForecast(tmfc, tmef, element)
                    .map { response -> element to GridDataParser.parseGridData(response) }
            }, 3)
            .collectMap({ it.first }, { it.second })
            .map { gridMap -> combineShortGrids(
                gridMap[ForecastElement.TMP]!!, gridMap[ForecastElement.TMX]!!, gridMap[ForecastElement.TMN]!!,
                gridMap[ForecastElement.VEC]!!, gridMap[ForecastElement.WSD]!!, gridMap[ForecastElement.SKY]!!,
                gridMap[ForecastElement.PTY]!!, gridMap[ForecastElement.POP]!!, gridMap[ForecastElement.PCP]!!,
                gridMap[ForecastElement.SNO]!!, gridMap[ForecastElement.REH]!!
            )}
    }

    /**
     * 각 요소별 격자 데이터를 하나의 ShortGridCell 격자로 합치는 함수.
     */
    private fun combineShortGrids(
        tmpGrid: MutableList<MutableList<Double?>>, tmxGrid: MutableList<MutableList<Double?>>,
        tmnGrid: MutableList<MutableList<Double?>>, vecGrid: MutableList<MutableList<Double?>>,
        wsdGrid: MutableList<MutableList<Double?>>, skyGrid: MutableList<MutableList<Double?>>,
        ptyGrid: MutableList<MutableList<Double?>>, popGrid: MutableList<MutableList<Double?>>,
        pcpGrid: MutableList<MutableList<Double?>>, snoGrid: MutableList<MutableList<Double?>>,
        rehGrid: MutableList<MutableList<Double?>>
    ): MutableList<MutableList<ShortGridCell>> {
        val numRows = tmpGrid.size
        val numCols = if (numRows > 0) tmpGrid[0].size else 0
        val combined = mutableListOf<MutableList<ShortGridCell>>()
        for (i in 0 until numRows) {
            val row = mutableListOf<ShortGridCell>()
            for (j in 0 until numCols) {
                row.add(ShortGridCell(
                    tmp = tmpGrid[i][j]?.toInt(), tmx = tmxGrid[i][j]?.toInt(), tmn = tmnGrid[i][j]?.toInt(),
                    vec = vecGrid[i][j]?.toFloat(), wsd = wsdGrid[i][j]?.toFloat(), sky = skyGrid[i][j]?.toInt(),
                    pty = ptyGrid[i][j]?.toInt(), pop = popGrid[i][j]?.toInt(), pcp = pcpGrid[i][j]?.toFloat(),
                    sno = snoGrid[i][j]?.toFloat(), reh = rehGrid[i][j]?.toInt()
                ))
            }
            combined.add(row)
        }
        return combined
    }
}