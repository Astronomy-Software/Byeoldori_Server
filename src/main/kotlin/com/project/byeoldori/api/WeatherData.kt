package com.project.byeoldori.api

import com.project.byeoldori.config.WeatherApiProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class WeatherData(
    @Qualifier("weatherApiClient")
    private val weatherApiClient: WebClient,
    private val weatherApiProperties: WeatherApiProperties  // API Key 주입
) {
    // AuthKey ".env"에서 가져옴
    private val weatherApiKey: String by lazy { weatherApiProperties.key }
    // 실황 조회
    fun fetchLiveWeather(tmfc: String, tmef: String, vars: String): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/cgi-bin/url/nph-dfs_odam_grd")
                    .queryParam("tmfc", tmfc)
                    .queryParam("tmef", tmef)
                    .queryParam("vars", vars)
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
    }

    // 초단기 격자 예보조회
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
    }

    // 중기 육상 예보 조회
    fun fetchMidLandForecast(): Mono<String> {
        print(weatherApiKey)
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/url/fct_afs_wl.php")
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
    }

    // 중기 기온 예보 조회
    fun fetchMidTemperatureForecast(): Mono<String> {
        return weatherApiClient.get()
            .uri { builder ->
                builder.path("/url/fct_afs_wc.php")
                    .queryParam("authKey", weatherApiKey)
                    .build()
            }
            .retrieve()
            .bodyToMono(String::class.java)
    }
}
