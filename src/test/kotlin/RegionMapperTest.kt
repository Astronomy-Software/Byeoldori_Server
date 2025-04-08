import com.project.byeoldori.region.RegionMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RegionMapperTest {

    @Test
    fun testGetSiByGrid_withValidCoordinates() {
        // 예시: JSON 파일에서 "서울특별시"가 격자 좌표 (60, 127) 근처에 매핑되어 있다고 가정
        val expectedRegionCode = "11B10101"
        val actualRegionCode = RegionMapper.getSiByGrid(60, 127)

        println("getSiByGrid(60, 127) → $actualRegionCode")
        assertNotNull(actualRegionCode, "격자 좌표에 매핑된 지역 코드가 null이면 안 됩니다.")
        assertEquals(expectedRegionCode, actualRegionCode, "예상 지역 코드와 일치해야 합니다.")
    }

    @Test
    fun testGetSiByLatLon_withValidCoordinates() {
        // 예시: 위경도 (37.56357, 126.98)가 "서울특별시"에 해당한다고 가정하고,
        // latLonToGrid 함수가 이 위경도를 (60, 127)로 변환한다고 가정합니다.
        val expectedRegionCode = "11B10101"
        val actualRegionCode = RegionMapper.getSiByLatLon(37.56357, 126.98)

        println("getSiByLatLon(37.56357, 126.98) → $actualRegionCode")
        assertNotNull(actualRegionCode, "위경도에 매핑된 지역 코드가 null이면 안 됩니다.")
        assertEquals(expectedRegionCode, actualRegionCode, "예상 지역 코드와 일치해야 합니다.")
    }

    @Test
    fun testGetSiByLatLon_withEdgeCase() {
        // 엣지 케이스 테스트: 경계에 가까운 좌표값에 대해 어떤 지역 코드가 반환되는지 확인
        // 실제 JSON 데이터에 맞춰 예상값을 설정하세요.
        val expectedRegionCode = "11B10103" // 예시 값
        val actualRegionCode = RegionMapper.getSiByLatLon(37.47, 126.87)

        println("getSiByLatLon(37.47, 126.87) → $actualRegionCode")
        assertNotNull(actualRegionCode, "경계에 가까운 좌표에 매핑된 지역 코드가 null이면 안 됩니다.")
        assertEquals(expectedRegionCode, actualRegionCode, "경계 조건에 맞는 지역 코드가 반환되어야 합니다.")
    }
}
