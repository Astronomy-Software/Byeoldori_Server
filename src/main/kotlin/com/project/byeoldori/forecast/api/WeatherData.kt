package com.project.byeoldori.forecast.api

import com.project.byeoldori.forecast.config.WeatherApiProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import org.slf4j.LoggerFactory
import java.time.Duration


@Component
class WeatherData(
    @Qualifier("weatherApiClient")
    private val weatherApiClient: WebClient,
    private val weatherApiProperties: WeatherApiProperties  // API Key 주입
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    // AuthKey ".env"에서 가져옴
    private val weatherApiKey: String by lazy { weatherApiProperties.key }
    // 응답 도중 연결이 끊긴 요청을 다시 보내기까지 기다리는 시간(테스트에서 줄인다)
    internal var transientRetryDelay: Duration = Duration.ofSeconds(2)

    /**
     * 응답 도중 연결이 끊긴 경우(PrematureClose 등)만 잠깐 뒤 한 번 더 요청한다.
     * 운영에서 하루 수 건씩 tmef 하나가 이 이유로 통째로 빠졌고, 단기는 3시간 주기라 그 시각이 다음 발표까지 비었다.
     * 연결 단계 실패·시간 초과·HTTP 오류는 재시도하지 않는다(KmaFailures.isTransientReadFailure).
     */
    private fun Mono<String>.retryTransientOnce(label: String): Mono<String> =
        retryWhen(
            Retry.fixedDelay(1, transientRetryDelay)
                .filter(KmaFailures::isTransientReadFailure)
                .doBeforeRetry { logger.info("$label 응답 중 연결이 끊겨 한 번 더 요청합니다: ${KmaFailures.describe(it.failure())}") }
                .onRetryExhaustedThrow { _, signal -> signal.failure() }
        )
    // 실황 조회
    fun fetchLiveWeather(tmfc: String, vars: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/cgi-bin/url/nph-dfs_odam_grd")
                    .queryParam("tmfc", tmfc)
                    .queryParam("vars", vars)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
    }

    fun fetchUltraShortForecast(tmfc: String, tmef: String, vars: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/cgi-bin/url/nph-dfs_vsrt_grd")
                    .queryParam("tmfc", tmfc)
                    .queryParam("tmef", tmef)
                    .queryParam("vars", vars)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .retryTransientOnce("초단기예보 tmef=$tmef vars=$vars")
            // 구독 시작 시점에 로그
            .doOnSubscribe {
                logger.info("초단기예보 API 호출 시작 - tmfc=$tmfc, tmef=$tmef, vars=$vars")
            }
            // 성공 시점에 로그
            .doOnSuccess {
                logger.info("초단기예보 API 호출 성공 - tmfc=$tmfc, tmef=$tmef, vars=$vars")
            }
            // 에러 발생 시점에 로그. 호출한 서비스가 tmef 단위로 건너뛰거나 주기 실패로 다시 기록하므로 여기선 WARN 한 줄
            .doOnError { error ->
                logger.warn("초단기예보 API 호출 실패 - tmfc=$tmfc, tmef=$tmef, vars=$vars: ${KmaFailures.describe(error)}")
            }
    }


    // 단기 격자 예보조회
    fun fetchShortForecast(tmfc: String, tmef: String, vars: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/cgi-bin/url/nph-dfs_shrt_grd")
                    .queryParam("tmfc", tmfc)
                    .queryParam("tmef", tmef)
                    .queryParam("vars", vars)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .retryTransientOnce("단기예보 tmef=$tmef vars=$vars")
            // 구독 시작 시점에 로그
            .doOnSubscribe {
                logger.info("단기예보 API 호출 시작 - tmfc=$tmfc, tmef=$tmef, vars=$vars")
            }
            // 성공 시점에 로그
            .doOnSuccess {
                logger.info("단기예보 API 호출 성공 - tmfc=$tmfc, tmef=$tmef, vars=$vars")
            }
            // 에러 발생 시점에 로그. 호출한 서비스가 tmef 단위로 건너뛰거나 주기 실패로 다시 기록하므로 여기선 WARN 한 줄
            .doOnError { error ->
                logger.warn("단기예보 API 호출 실패 - tmfc=$tmfc, tmef=$tmef, vars=$vars: ${KmaFailures.describe(error)}")
            }
    }

    // 중기 육상 예보 조회. tmfc1·tmfc2(발표시각 범위, yyyyMMddHH)는 둘 다 넘긴다 —
    // 2026-11-27 부터 기상청이 둘 중 하나라도 없으면 헤더만 돌려준다(그 전엔 없이도 최신 발표를 줬다).
    fun fetchMidLandForecast(tmfc1: String, tmfc2: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/url/fct_afs_wl.php")
                    .queryParam("tmfc1", tmfc1)
                    .queryParam("tmfc2", tmfc2)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSubscribe { logger.info("중기 육상 예보 API 호출 시작 - tmfc=$tmfc1~$tmfc2") }
            .doOnSuccess { logger.info("중기 육상 예보 API 호출 성공 - tmfc=$tmfc1~$tmfc2") }
            // 스케줄러가 재시도하고 모두 실패할 때만 ERROR 를 남기므로 여기선 WARN 한 줄(#192 와 같은 기준)
            .doOnError { logger.warn("중기 육상 예보 API 호출 실패 - tmfc=$tmfc1~$tmfc2: ${KmaFailures.describe(it)}") }
    }

    // 중기 기온 예보 조회(위와 같은 범위 규칙)
    fun fetchMidTemperatureForecast(tmfc1: String, tmfc2: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/url/fct_afs_wc.php")
                    .queryParam("tmfc1", tmfc1)
                    .queryParam("tmfc2", tmfc2)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
            .doOnSubscribe { logger.info("중기 기온 예보 API 호출 시작 - tmfc=$tmfc1~$tmfc2") }
            .doOnSuccess { logger.info("중기 기온 예보 API 호출 성공 - tmfc=$tmfc1~$tmfc2") }
            .doOnError { logger.warn("중기 기온 예보 API 호출 실패 - tmfc=$tmfc1~$tmfc2: ${KmaFailures.describe(it)}") }
    }
}
