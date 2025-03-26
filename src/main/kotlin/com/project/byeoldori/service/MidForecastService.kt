package com.project.byeoldori.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.api.WeatherData
import com.project.byeoldori.dto.MidForecastResponseDTO
import com.project.byeoldori.entity.MidForecast
import com.project.byeoldori.parser.MidForecastParser
import com.project.byeoldori.repository.MidForecastRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDateTime

@Service
class MidForecastService(
    private val weatherData: WeatherData,
    private val midForecastParser: MidForecastParser,
    private val midForecastRepository: MidForecastRepository,
    private val objectMapper: ObjectMapper
)

{
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun fetchParseSaveAndRespond(): Mono<String> {
        return weatherData.fetchMidLandForecast()
            .map { response ->
                val midForecastList = midForecastParser.parse(response)
                saveAll(midForecastList)

                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(midForecastList.map { it.toResponseDTO() })
            }
    }

    @Transactional
    fun saveAll(midForecastList: List<MidForecast>): List<MidForecastResponseDTO> {
        val filteredList = midForecastList.filter { forecast ->
            !midForecastRepository.existsByTmFcAndTmEfAndRegId(
                forecast.tmFc,
                forecast.tmEf,
                forecast.regId
            )
        }

        val newList = filteredList.map { forecast ->
            forecast.copy(id = 0)
        }

        val savedList = if (newList.isNotEmpty()) {
            midForecastRepository.saveAll(newList)
        } else {
            emptyList()
        }

        logger.info("중기 예보 ${savedList.size}건 저장 완료!")

        return savedList.map { it.toResponseDTO() }
    }

    fun findAll(): List<MidForecastResponseDTO> {
        return midForecastRepository.findAll()
            .map { it.toResponseDTO() }
    }

    @Transactional
    fun deleteOldForecasts() {
        val cutoffTime = LocalDateTime.now().minusHours(24)
        midForecastRepository.deleteByCreatedAtBefore(cutoffTime)
    }

    private fun MidForecast.toResponseDTO() = MidForecastResponseDTO(
        regId = regId,
        tmFc = tmFc,
        tmEf = tmEf,
        modCode = modCode,
        stn = stn,
        c = c,
        sky = sky,
        pre = pre,
        conf = conf,
        wf = wf,
        rnSt = rnSt
    )
}
