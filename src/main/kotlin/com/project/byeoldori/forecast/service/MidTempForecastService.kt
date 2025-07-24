package com.project.byeoldori.forecast.service

import com.fasterxml.jackson.databind.ObjectMapper
    import com.project.byeoldori.forecast.api.WeatherData
    import com.project.byeoldori.forecast.dto.MidTempForecastResponseDTO
    import com.project.byeoldori.forecast.entity.MidTempForecast
    import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
    import com.project.byeoldori.forecast.repository.MidTempForecastRepository
    import org.slf4j.LoggerFactory
    import org.springframework.stereotype.Service
    import org.springframework.transaction.annotation.Transactional
    import reactor.core.publisher.Mono
    import java.time.LocalDateTime

@Service
class MidTempForecastService(
    private val weatherData: WeatherData,
    private val midTempForecastParser: MidTempForecastParser,
    private val midTempForecastRepository: MidTempForecastRepository,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun fetchParseSaveAndRespond(): Mono<String> {
        return weatherData.fetchMidTemperatureForecast()
            .map { response ->
                val midTempForecastList = midTempForecastParser.parse(response)
                saveAll(midTempForecastList)

                objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(midTempForecastList.map { it.toResponseDTO() })
            }
    }

    @Transactional
    fun saveAll(midTempForecastList: List<MidTempForecast>): List<MidTempForecastResponseDTO> {
        val filteredList = midTempForecastList.filter { forecast ->
            !midTempForecastRepository.existsByTmFcAndTmEfAndRegId(
                forecast.tmFc,
                forecast.tmEf,
                forecast.regId
            )
        }

        val newList = filteredList.map { forecast -> forecast.copy(id = 0) }

        val savedList = if (newList.isNotEmpty()) {
            midTempForecastRepository.saveAll(newList)
        } else {
            emptyList()
        }

        logger.info("중기 기온 예보 ${savedList.size}건 저장 완료!")

        return savedList.map { it.toResponseDTO() }
    }

    fun findAll(): List<MidTempForecastResponseDTO> {
        return midTempForecastRepository.findAll()
            .map { it.toResponseDTO() }
    }

    @Transactional
    fun deleteOldForecasts() {
        val cutoffTime = LocalDateTime.now().minusHours(24)
        val deletedCount = midTempForecastRepository.deleteByCreatedAtBefore(cutoffTime)

        logger.info("24시간 지난 중기 기온 예보 $deletedCount 건 삭제 완료")
    }

    fun findAllEntity(): List<MidTempForecast> {
        return midTempForecastRepository.findAll()
    }

    fun MidTempForecast.toResponseDTO() = MidTempForecastResponseDTO(
        regId = regId,
        tmFc = tmFc,
        tmEf = tmEf,
        min = min,
        max = max
    )
}