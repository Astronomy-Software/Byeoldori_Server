package com.project.byeoldori.domain.midforecast

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MidForecastService(
    private val midForecastRepository: MidForecastRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

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
            forecast.copy(id = null)
        }

        val savedList = if (newList.isNotEmpty()) {
            midForecastRepository.saveAll(newList)
        } else {
            emptyList()
        }

        return savedList.map { forecast ->
            MidForecastResponseDTO(
                regId = forecast.regId,
                tmFc = forecast.tmFc,
                tmEf = forecast.tmEf,
                modCode = forecast.modCode,
                stn = forecast.stn,
                c = forecast.c,
                sky = forecast.sky,
                pre = forecast.pre,
                conf = forecast.conf,
                wf = forecast.wf,
                rnSt = forecast.rnSt
            )
        }
    }

    fun findAll(): List<MidForecastResponseDTO> {
        return midForecastRepository.findAll().map { forecast ->
            MidForecastResponseDTO(
                regId = forecast.regId,
                tmFc = forecast.tmFc,
                tmEf = forecast.tmEf,
                modCode = forecast.modCode,
                stn = forecast.stn,
                c = forecast.c,
                sky = forecast.sky,
                pre = forecast.pre,
                conf = forecast.conf,
                wf = forecast.wf,
                rnSt = forecast.rnSt
            )
        }
    }
}
