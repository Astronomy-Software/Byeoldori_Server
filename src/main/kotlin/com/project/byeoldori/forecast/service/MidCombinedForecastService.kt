package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.MidCombinedForecastDTO
import com.project.byeoldori.forecast.entity.MidCombinedForecast
import com.project.byeoldori.forecast.entity.MidForecast
import com.project.byeoldori.forecast.entity.MidTempForecast
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import com.project.byeoldori.forecast.utils.region.RegionMapper
import com.project.byeoldori.forecast.repository.MidCombinedForecastRepository
import com.project.byeoldori.forecast.utils.logging.LogMessages
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MidCombinedForecastService(
    private val midForecastParser: MidForecastParser,
    private val midTempForecastParser: MidTempForecastParser,
    private val combinedForecastRepository: MidCombinedForecastRepository,
    private val weatherData: WeatherData
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun fetchAndSaveFromApi(): String {
        val landResponse = weatherData.fetchMidLandForecast().block() ?: throw IllegalStateException("기상청 육상 API 호출 실패")
        val tempResponse =
            weatherData.fetchMidTemperatureForecast().block() ?: throw IllegalStateException("기상청 기온 API 호출 실패")

        val landList = midForecastParser.parse(landResponse)
        val tempList = midTempForecastParser.parse(tempResponse)

        val combinedList = mergeForecasts(landList, tempList)
        saveAll(combinedList)

        return "기상청 API에서 데이터 수집 후 병합 및 저장 완료 (${combinedList.size}건)"
    }

    fun mergeForecasts(
        landList: List<MidForecast>,
        tempList: List<MidTempForecast>
    ): List<MidCombinedForecast> {

        // 기온 예보는 00시(TMEf = xxxx0000)만 존재 → 이를 기준으로 병합
        val tempMap = tempList
            .filter { it.tmEf.endsWith("0000") }
            .associateBy { Pair(it.tmEf, it.regId) } // (tmEf, siRegId) → MidTempForecast

        return landList.mapNotNull { land ->
            // 육상 예보는 도(regId) 기준 → 해당 도에 속하는 모든 시 리스트 조회
            val siList = RegionMapper.getSiListByDo(land.regId) ?: return@mapNotNull null

            // 시 단위로 반복하여 병합 예보 생성
            siList.mapNotNull { siRegId ->
                // 육상 예보의 날짜 (tmEf) 기반으로 00시 기온 참조
                val forecastDate = land.tmEf.substring(0, 8) + "0000"
                val temp = tempMap[Pair(forecastDate, siRegId)]

                MidCombinedForecast(
                    tmFc = land.tmFc,
                    tmEf = land.tmEf,
                    doRegId = land.regId,
                    siRegId = siRegId,
                    sky = land.sky,
                    pre = land.pre,
                    rnSt = land.rnSt,
                    min = temp?.min, // 없으면 null
                    max = temp?.max
                )
            }
        }.flatten()
    }

    @Transactional
    fun saveAll(forecastList: List<MidCombinedForecast>) {
        forecastList.forEach { forecast ->
            val existing = combinedForecastRepository
                .findByTmFcAndTmEfAndSiRegId(forecast.tmFc, forecast.tmEf, forecast.siRegId)

            if (existing != null) {
                combinedForecastRepository.delete(existing)
                logger.info(
                    String.format(LogMessages.DELETE_FORECAST, forecast.tmFc, forecast.tmEf, forecast.siRegId)
                )
            }

            combinedForecastRepository.save(forecast)
            logger.info(
                String.format(LogMessages.SAVE_FORECAST, forecast.tmFc, forecast.tmEf, forecast.siRegId)
            )
        }
    }

    @Transactional
    fun deleteOldForecasts() {
        val cutoffTime = LocalDateTime.now().minusHours(24)
        combinedForecastRepository.deleteByCreatedAtBefore(cutoffTime)
    }

    fun findAll(): List<MidCombinedForecastDTO> {
        val forecasts = combinedForecastRepository.findAll()
        return forecasts.map { forecast ->
            MidCombinedForecastDTO(
                tmFc = forecast.tmFc,
                tmEf = forecast.tmEf,
                doRegId = forecast.doRegId,
                siRegId = forecast.siRegId,
                sky = forecast.sky,
                pre = forecast.pre,
                rnSt = forecast.rnSt,
                min = forecast.min,
                max = forecast.max
            )
        }
    }
}