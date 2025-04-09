package com.project.byeoldori.forecast.controller

import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.dto.*
import com.project.byeoldori.forecast.service.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/weather")
class WeatherController(
    private val weatherData: WeatherData,
    private val midForecastService: MidForecastService,
    private val ultraForecastService: UltraGridForecastService,
    private val shortForecastService: ShortGridForecastService,
    private val foreCastService: ForeCastService,
    private val midTempForecastService: MidTempForecastService,
    private val midCombinedForecastService: MidCombinedForecastService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Operation(summary = "실시간 날씨 조회", description = "기상청 실황 날씨 데이터를 호출합니다.")
    @GetMapping("/live")
    fun getLiveWeather(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchLiveWeather(tmfc, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "초단기 예보 조회", description = "기상청 초단기 예보 데이터를 호출합니다.")
    @GetMapping("/ultra-short-term")
    fun getUltraShortTermForecast(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "발효시간 (tmef)") @RequestParam tmef: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchUltraShortForecast(tmfc, tmef, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "단기 예보 조회", description = "기상청 단기 예보 데이터를 호출합니다.")
    @GetMapping("/short-term")
    fun getShortTermForecast(
        @Parameter(description = "발표시간 (tmfc)") @RequestParam tmfc: String,
        @Parameter(description = "발효시간 (tmef)") @RequestParam tmef: String,
        @Parameter(description = "조회할 날씨 데이터 (vars)") @RequestParam vars: String
    ): Mono<ResponseEntity<String>> {
        return weatherData.fetchShortForecast(tmfc, tmef, vars)
            .map { ResponseEntity.ok(it) }
    }

    @Operation(summary = "중기 육상 예보 조회 및 저장", description = "기상청 중기 육상 예보 데이터를 호출 후 DB에 저장합니다.")
    @GetMapping("/mid-land")
    fun getAndSaveMidLandForecast(): Mono<ResponseEntity<String>> {
        return midForecastService.fetchParseSaveAndRespond()
            .map { ResponseEntity.ok(it) } // 원본 데이터 응답
    }

    @Operation(summary = "저장된 중기 육상 예보 전체 조회", description = "DB에 저장된 중기 육상 예보 데이터를 모두 조회합니다.")
    @GetMapping("/mid-land/all")
    fun getAllMidLandForecasts(): ResponseEntity<List<MidForecastResponseDTO>> {
        val forecasts = midForecastService.findAll()
        return ResponseEntity.ok(forecasts)
    }

    @Operation(summary = "중기 기온 예보 조회", description = "기상청 중기 기온 예보 데이터를 호출 후 DB에 저장합니다.")
    @GetMapping("/mid-temp")
    fun getMidTemperatureForecast(): Mono<ResponseEntity<String>> {
        return midTempForecastService.fetchParseSaveAndRespond()
            .map { ResponseEntity.ok(it) }
    }

    @GetMapping("/updateUltraForecastData")
    fun updateUltraForecastData() {
        val tmefList = listOf(
            "202503250300", "202503250400", "202503250500", "202503250600", "202503250700", "202503250800"
        )
        ultraForecastService.updateAllUltraTMEFData(tmfc = "202503250200",tmefList)
    }

    @GetMapping("/updateShortForecastData")
    fun updateShortForecastData() {
        val tmfcTime = "202503252300"
        val timeList = listOf(
            "202503260500", "202503260600", "202503260700", "202503260800", "202503260900", "202503261000",
            "202503261100", "202503261200", "202503261300", "202503261400", "202503261500", "202503261600",
            "202503261700", "202503261800", "202503261900", "202503262000", "202503262100", "202503262200",
            "202503262300", "202503270000", "202503270100", "202503270200", "202503270300", "202503270400",
            "202503270500", "202503270600", "202503270700", "202503270800", "202503270900", "202503271000",
            "202503271100", "202503271200", "202503271300", "202503271400", "202503271500", "202503271600",
            "202503271700", "202503271800", "202503271900", "202503272000", "202503272100", "202503272200",
            "202503272300", "202503280000"
        )
        shortForecastService.updateAllShortTMEFData(tmfcTime, timeList)
    }

    @GetMapping("/UltraForecastCellData")
    fun getUltraForecastCellData(
        @RequestParam x: Int,
        @RequestParam y: Int
    ): List<UltraForecastResponseDTO> {
        logger.info("x좌표 $x Y좌표 $y")
        return ultraForecastService.getAllUltraTMEFDataForCell(x, y)
    }

    @GetMapping("/ShortForecastCellData")
    fun getShortForecastCellData(
        @RequestParam x: Int,
        @RequestParam y: Int
    ): List<ShortForecastResponseDTO> {
        logger.info("x좌표 $x Y좌표 $y")
        return shortForecastService.getAllShortTMEFDataForCell(x, y)
    }

    @GetMapping("/ForecastData")
    fun getForecastData(
        @RequestParam lat: Double,
        @RequestParam long: Double,
    ): ForecastResponseDTO {
        logger.info("ForecastData 호출 lat $lat, long $long")
        return foreCastService.getForecastDataByLocation(lat, long)
    }

    @Operation(summary = "중기 육상 + 기온 예보 조회", description = "기상청 중기 육상 + 예보 데이터를 호출 후 DB에 저장합니다.")
    @PostMapping("/mid-combined")
    fun fetchAndSaveMidCombinedForecastFromApi(): ResponseEntity<String> {
        val result = midCombinedForecastService.fetchAndSaveFromApi()
        return ResponseEntity.ok(result)
    }

    @Operation(summary = "저장된 중기 예보 전체 조회", description = "DB에 저장된 병합된 중기 육상 및 기온 데이터를 모두 조회합니다.")
    @GetMapping("/mid-combined/all")
    fun getAllSavedForecasts(): ResponseEntity<List<MidCombinedForecastDTO>> {
        val dtoList = midCombinedForecastService.findAll()
        return ResponseEntity.ok(dtoList)
    }
}