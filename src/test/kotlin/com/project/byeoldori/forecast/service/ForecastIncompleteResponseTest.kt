package com.project.byeoldori.forecast.service

import com.project.byeoldori.forecast.api.KmaReachability
import com.project.byeoldori.forecast.api.WeatherData
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotStore
import com.project.byeoldori.forecast.snapshot.ForecastSnapshotTest
import com.project.byeoldori.forecast.utils.forecasts.ForecastElement
import com.project.byeoldori.forecast.utils.forecasts.GridDataParser
import com.project.byeoldori.forecast.utils.forecasts.MidForecastParser
import com.project.byeoldori.forecast.utils.forecasts.MidTempForecastParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

/**
 * 기상청 응답 일부가 비었거나 형식이 다를 때의 처리.
 * 2026-11-04 11시대에 실황 10시 발표분의 한 요소가 0행으로 와서 격자 결합이 인덱스 예외로 깨지고,
 * 1시간 동안 실황이 갱신되지 않은 채 주기마다 5회씩 재시도한 일을 재현한다.
 */
class ForecastIncompleteResponseTest {

    private fun grid(value: Int) = (0 until 149 * 2).joinToString(",") { "$value.0" }
    @Volatile private var value = 7
    private val full get() = grid(value)
    private val calls = AtomicInteger()
    private val emptyElements = mutableSetOf<String>()

    private val weatherData: WeatherData = mock(WeatherData::class.java).also {
        `when`(it.fetchLiveWeather(anyString(), anyString())).thenAnswer { inv ->
            calls.incrementAndGet()
            Mono.just(if (inv.getArgument<String>(1) in emptyElements) "" else full)
        }
    }
    private val live = LiveGridForecastService(
        weatherData, ForecastSnapshotStore(ForecastSnapshotTest.MemoryBlobStore()), KmaReachability()
    )

    @Test
    fun `실황 한 요소가 비면 그 요소만 직전 값으로 채우고 나머지는 갱신한다(예외·재시도 없음)`() {
        live.updateLiveData("202611040900").block()
        assertThat(live.getLiveDataForCell(0, 0)?.t1h).isEqualTo(7)

        // 다음 발표분: 값은 9 로 바뀌었는데 SKY 만 비었다(2026-11-07~08 의 17시간 공백과 같은 모양)
        value = 9
        emptyElements += ForecastElement.SKY
        calls.set(0)
        val error = catchThrowable { live.updateLiveData("202611041000").block() }

        assertThat(error).isNull()                 // 예외가 없으니 스케줄러가 즉시 재시도하지 않는다
        assertThat(calls.get()).isEqualTo(7)       // 요소당 1회, 추가 호출 없음
        val cell = live.getLiveDataForCell(0, 0)!!
        assertThat(cell.t1h).isEqualTo(9)          // 정상 요소는 새 값
        assertThat(cell.vec).isEqualTo(9)
        assertThat(cell.sky).isEqualTo(7)          // 빈 요소는 직전 값 유지

        // 공백이 이어져도 같은 방식으로 계속 갱신된다
        value = 11
        live.updateLiveData("202611041100").block()
        assertThat(live.getLiveDataForCell(0, 0)?.t1h).isEqualTo(11)
        assertThat(live.getLiveDataForCell(0, 0)?.sky).isEqualTo(7)

        emptyElements.clear()
        live.updateLiveData("202611041200").block()
        assertThat(live.getLiveDataForCell(0, 0)?.sky).isEqualTo(11)
    }

    @Test
    fun `직전 격자가 없거나 모든 요소가 비면 이번 갱신만 건너뛴다`() {
        emptyElements += ForecastElement.SKY
        assertThat(catchThrowable { live.updateLiveData("202611040900").block() }).isNull()
        assertThat(live.getLiveDataForCell(0, 0)).isNull()  // 채울 직전 값이 없음

        emptyElements.clear()
        live.updateLiveData("202611041000").block()
        assertThat(live.getLiveDataForCell(0, 0)?.t1h).isEqualTo(7)

        emptyElements += listOf(ForecastElement.T1H, ForecastElement.VEC, ForecastElement.WSD, ForecastElement.PTY,
            ForecastElement.RN1, ForecastElement.REH, ForecastElement.SKY)
        value = 9
        assertThat(catchThrowable { live.updateLiveData("202611041100").block() }).isNull()
        assertThat(live.getLiveDataForCell(0, 0)?.t1h).isEqualTo(7)  // 전부 비면 기존 캐시 그대로
    }

    @Test
    fun `격자 크기 검사는 비었거나 행·열 수가 다른 요소만 골라낸다`() {
        val ok = GridDataParser.parseGridData(full)
        val short = GridDataParser.parseGridData((0 until 149).joinToString(",") { "1" })

        assertThat(GridDataParser.incompleteGrids(mapOf("A" to ok, "B" to ok))).isEmpty()
        assertThat(GridDataParser.incompleteGrids(mapOf("A" to ok, "B" to mutableListOf(), "C" to short)))
            .containsExactly("B", "C")
        assertThat(GridDataParser.incompleteGrids(mapOf("A" to mutableListOf<MutableList<Double?>>())))
            .containsExactly("A")
    }

    @Test
    fun `중기 육상 예보는 열이 모자란 줄만 건너뛰고 정상 줄은 남긴다`() {
        val response = """
            #START7777
            # REG_ID TM_FC TM_EF MOD STN C SKY PRE CONF WF RN_ST
            11B00000 202611041800 202611080000 A02 109 1 WB01 WB00 0 "맑음" 10
            일시적인 오류입니다
            11D10000 202611041800 202611080000 A02 105 1 WB03 WB01 0 "구름많음" 30
            #7777END
        """.trimIndent()

        val parsed = MidForecastParser().parse(response)

        assertThat(parsed.map { it.regId }).containsExactly("11B00000", "11D10000")
        assertThat(parsed.map { it.rnSt }).containsExactly(10, 30)
    }

    @Test
    fun `중기 기온 예보도 열이 모자란 줄만 건너뛴다`() {
        val response = """
            # REG_ID TM_FC TM_EF MOD STN C MIN MAX MIN_L MIN_H MAX_L MAX_H
            11B10101 202611041800 202611080000 A02 109 0 5 15 3 7 13 17
            잠시 후 다시
        """.trimIndent()

        val parsed = MidTempForecastParser().parse(response)

        assertThat(parsed).hasSize(1)
        assertThat(parsed[0].min).isEqualTo(5)
        assertThat(parsed[0].max).isEqualTo(15)
    }

    @Test
    fun `정상 줄이 하나도 없는 응답은 예외로 두어 재시도되게 한다`() {
        assertThatThrownBy { MidForecastParser().parse("API 키가 유효하지 않습니다") }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { MidTempForecastParser().parse("error\n#7777END") }
            .isInstanceOf(IllegalStateException::class.java)
        // 주석뿐인 빈 응답은 기존처럼 빈 목록
        assertThat(MidForecastParser().parse("#START7777\n#7777END")).isEmpty()
    }
}
