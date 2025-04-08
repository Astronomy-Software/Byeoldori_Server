package com.project.byeoldori.region

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class RegionMapping(
    @JsonProperty("시/군")
    val regionName: String,
    @JsonProperty("시지역코드")
    val regionCode: String,
    @JsonProperty("위도")
    val latitude: Double,
    @JsonProperty("경도")
    val longitude: Double,
    val x: Int,
    val y: Int
)

data class RegionsWrapper(
    val regions: List<RegionMapping>
)

object RegionMappingLoader {
    private val mapper = jacksonObjectMapper()
    val regions: List<RegionMapping> by lazy {
        val inputStream = this::class.java.classLoader.getResourceAsStream("xy.json")
            ?: throw IllegalStateException("xy.json 파일을 찾을 수 없습니다.")
        val wrapper: RegionsWrapper = mapper.readValue(inputStream)
        wrapper.regions
    }
}
