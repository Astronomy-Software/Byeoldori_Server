package com.project.byeoldori.forecast.utils.region

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * 중기예보 지역 코드 매핑(도↔시)과 격자 좌표→시 코드 최근접 탐색. xy.json 리소스를 그대로 읽는다.
 */
class RegionMapperTest {

    @Test
    fun `시 코드로 도 코드를 찾는다`() {
        assertThat(RegionMapper.getDoBySi("11B10101")).isEqualTo("11B00000") // 서울
        assertThat(RegionMapper.getDoBySi("11H20201")).isEqualTo("11H20000") // 부산
        assertThat(RegionMapper.getDoBySi("11E00101")).isEqualTo("11H10000") // 울릉도 → 경북
        assertThat(RegionMapper.getDoBySi("21F10501")).isEqualTo("11F20000") // 전남권
    }

    @Test
    fun `모르는 시 코드는 null`() {
        assertThat(RegionMapper.getDoBySi("99Z99999")).isNull()
        assertThat(RegionMapper.getDoBySi("")).isNull()
    }

    @Test
    fun `도 코드로 시 목록을 찾는다`() {
        val seoulArea = RegionMapper.getSiListByDo("11B00000")
        assertThat(seoulArea).isNotNull.contains("11B10101", "11B20201")
        assertThat(RegionMapper.getSiListByDo("11I00000")).containsExactly("11I10001", "11I10002", "11I20001", "11I20002", "11I20003")
        assertThat(RegionMapper.getSiListByDo("00000000")).isNull()
    }

    @Test
    fun `시→도 역매핑은 도→시 매핑과 1대1 이다(시 코드가 두 도에 중복되지 않는다)`() {
        val allSi = RegionMapper.doToSiMap.values.flatten()
        assertThat(allSi).doesNotHaveDuplicates()
        assertThat(RegionMapper.siToDoMap).hasSize(allSi.size)
        RegionMapper.doToSiMap.forEach { (doId, siList) ->
            siList.forEach { si -> assertThat(RegionMapper.siToDoMap[si]).`as`(si).isEqualTo(doId) }
        }
    }

    @Test
    fun `xy_json 의 모든 시 코드는 어떤 도에든 속한다`() {
        val orphan = RegionMappingLoader.regions.map { it.regionCode }.filter { RegionMapper.getDoBySi(it) == null }
        assertThat(orphan).`as`("도 매핑이 없는 시 코드").isEmpty()
    }

    @Test
    fun `격자 좌표가 정확히 일치하면 그 지역, 어긋나면 가장 가까운 지역`() {
        assertThat(RegionMapper.getSiByGrid(60, 127)).isEqualTo("11B10101") // 서울 (60,127)
        assertThat(RegionMapper.getSiByGrid(21, 135)).isEqualTo("11A00101") // 백령도
        // 서울 격자에서 1칸 어긋난 좌표도 근접 지역(서울 또는 바로 옆 시)으로 매핑된다
        val near = RegionMapper.getSiByGrid(60, 128)
        assertThat(near).isNotNull()
        val region = RegionMappingLoader.regions.first { it.regionCode == near }
        assertThat(Math.abs(region.x - 60) + Math.abs(region.y - 128)).isLessThanOrEqualTo(2)
    }

    @Test
    fun `격자 범위를 크게 벗어나도 null 이 아니라 가장 가까운 지역을 돌려준다`() {
        assertThat(RegionMapper.getSiByGrid(0, 0)).isNotNull()
        assertThat(RegionMapper.getSiByGrid(1000, 1000)).isNotNull()
    }

    @Test
    fun `위경도로 시 코드를 찾는다`() {
        assertThat(RegionMapper.getSiByLatLon(37.5665, 126.9780)).isEqualTo("11B10101") // 서울시청
        assertThat(RegionMapper.getSiByLatLon(35.1796, 129.0756)).isEqualTo("11H20201") // 부산
        assertThat(RegionMapper.getSiByLatLon(33.4996, 126.5312)).isEqualTo("11G00201") // 제주
    }

    @Test
    fun `지역 매핑 데이터는 코드가 중복되지 않고 좌표가 한국 범위 안이다`() {
        val regions = RegionMappingLoader.regions
        assertThat(regions).isNotEmpty
        assertThat(regions.map { it.regionCode }).doesNotHaveDuplicates()
        // 이어도(11G00601)만 위경도가 0.0 자리표시자다 — 격자 (28, 8) 만 쓰이므로 동작엔 영향 없다
        val placeholders = regions.filter { it.latitude == 0.0 && it.longitude == 0.0 }
        assertThat(placeholders.map { it.regionName }).containsExactly("이어도")
        (regions - placeholders).forEach {
            assertThat(GeoBounds.isInKorea(it.latitude, it.longitude)).`as`(it.regionName).isTrue()
        }
        regions.forEach {
            assertThat(it.x).`as`(it.regionName).isBetween(1, 149)
            assertThat(it.y).`as`(it.regionName).isBetween(1, 253)
        }
    }
}
