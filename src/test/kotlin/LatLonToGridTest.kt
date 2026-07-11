import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * latLonToGrid 좌표→격자 변환 검증 (기상청 DFS Lambert Conformal Conic, 순수 함수).
 * 기대 격자값은 소스에 명시된 상수(RE/GRID/SLAT/OLON/OLAT/XO/YO)로 결정되며,
 * 잘 알려진 좌표에 대해 재현 계산한 값을 사용한다.
 * latLonToGrid.kt 는 패키지 선언이 없어 디폴트 패키지에 속하므로 본 테스트도 디폴트 패키지에 둔다.
 */
class LatLonToGridTest {

    @Test
    fun `서울 좌표는 격자 60 127 로 변환된다`() {
        assertThat(latLonToGrid(37.5665, 126.9780)).isEqualTo(Pair(60, 127))
    }

    @Test
    fun `부산 좌표는 격자 98 76 로 변환된다`() {
        assertThat(latLonToGrid(35.1796, 129.0756)).isEqualTo(Pair(98, 76))
    }

    @Test
    fun `대전 좌표는 격자 67 100 로 변환된다`() {
        assertThat(latLonToGrid(36.3504, 127.3845)).isEqualTo(Pair(67, 100))
    }

    @Test
    fun `기준 위경도(OLAT 38, OLON 126)는 기준 격자(XO 43, YO 136)로 변환된다`() {
        assertThat(latLonToGrid(38.0, 126.0)).isEqualTo(Pair(43, 136))
    }

    @Test
    fun `NaN 위경도는 예외를 던진다`() {
        assertThatThrownBy { latLonToGrid(Double.NaN, 126.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { latLonToGrid(37.5, Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `위도 범위를 벗어나면 예외를 던진다`() {
        assertThatThrownBy { latLonToGrid(95.0, 126.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { latLonToGrid(-91.0, 126.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `경도 범위를 벗어나면 예외를 던진다`() {
        assertThatThrownBy { latLonToGrid(37.5, 200.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { latLonToGrid(37.5, -181.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `한국 밖의 유효 위경도는 격자 범위를 벗어나 예외를 던진다`() {
        // 도쿄 부근 - 위경도 자체는 유효하나 KMA 격자(1..149, 1..253) 밖
        assertThatThrownBy { latLonToGrid(0.0, 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
