package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.LiveForecastResponseDTO
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
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

@Service
class LiveGridForecastService(
    private val weatherData: WeatherData
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var liveGrid: MutableList<MutableList<LiveGridCell>> = mutableListOf()
    private val lock = ReentrantReadWriteLock()

    fun updateLiveData(tmfc: String) {
        Mono.zip(
            weatherData.fetchLiveWeather(tmfc, ForecastElement.T1H),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.VEC),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.WSD),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.PTY),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.RN1),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.REH),
            weatherData.fetchLiveWeather(tmfc, ForecastElement.SKY),
        ).map { t ->
            combineGrids(
                GridDataParser.parseGridData(t.t1),
                GridDataParser.parseGridData(t.t2),
                GridDataParser.parseGridData(t.t3),
                GridDataParser.parseGridData(t.t4),
                GridDataParser.parseGridData(t.t5),
                GridDataParser.parseGridData(t.t6),
                GridDataParser.parseGridData(t.t7),
            )
        }.subscribe(
            { grid ->
                lock.write { liveGrid = grid }
                logger.info("실황 격자 데이터 업데이트 완료 (tmfc=$tmfc)")
            },
            { e -> logger.error("실황 격자 데이터 업데이트 실패", e) }
        )
    }

    fun getLiveDataForCell(x: Int, y: Int): LiveForecastResponseDTO? {
        return lock.read {
            val cell = findNearest(x, y) ?: return@read null
            LiveForecastResponseDTO(
                t1h = cell.t1h, vec = cell.vec, wsd = cell.wsd,
                pty = cell.pty, rn1 = cell.rn1, reh = cell.reh, sky = cell.sky
            )
        }
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
}
