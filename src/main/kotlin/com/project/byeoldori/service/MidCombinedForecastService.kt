package com.project.byeoldori.service

import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.dto.MidCombinedForecastDTO
import com.project.byeoldori.entity.MidCombinedForecast
import com.project.byeoldori.entity.MidForecast
import com.project.byeoldori.entity.MidTempForecast
import com.project.byeoldori.parser.MidForecastParser
import com.project.byeoldori.parser.MidTempForecastParser
import com.project.byeoldori.region.RegionMapper
import com.project.byeoldori.repository.MidCombinedForecastRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    private fun mergeForecasts(
        landList: List<MidForecast>,
        tempList: List<MidTempForecast>
    ): List<MidCombinedForecast> {

        val landMap = landList.groupBy { Pair(it.tmFc, it.tmEf) }

        return tempList.mapNotNull { temp ->
            val doRegId = RegionMapper.getDoBySi(temp.regId)

            if (doRegId == null) {
                logger.warn("시 지역(${temp.regId})에 대한 도 지역 매핑을 찾을 수 없습니다.")
                return@mapNotNull null
            }

            val matchingLand = landMap[Pair(temp.tmFc, temp.tmEf)]?.find {
                it.regId == doRegId
            }

            if (matchingLand == null) {
                logger.warn("도 지역(${doRegId})에 대한 육상 예보를 찾을 수 없습니다. [tmFc=${temp.tmFc}, tmEf=${temp.tmEf}]")
                return@mapNotNull null
            }

            MidCombinedForecast(
                tmFc = temp.tmFc,
                tmEf = temp.tmEf,
                doRegId = doRegId,
                siRegId = temp.regId,
                sky = matchingLand.sky,
                pre = matchingLand.pre,
                wf = matchingLand.wf,
                rnSt = matchingLand.rnSt,
                min = temp.min,
                max = temp.max
            )
        }
    }

    @Transactional
    fun saveAll(forecastList: List<MidCombinedForecast>) {
        val filteredList = forecastList.filter {
            !combinedForecastRepository.existsByTmFcAndTmEfAndSiRegId(
                it.tmFc, it.tmEf, it.siRegId
            )
        }

        if (filteredList.isNotEmpty()) {
            combinedForecastRepository.saveAll(filteredList)
            logger.info("신규 병합 예보 ${filteredList.size}건 저장 완료!")
        } else {
            logger.info("신규 저장할 병합 예보 데이터가 없습니다.")
        }
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
                wf = forecast.wf,
                rnSt = forecast.rnSt,
                min = forecast.min,
                max = forecast.max
            )
        }
    }
}