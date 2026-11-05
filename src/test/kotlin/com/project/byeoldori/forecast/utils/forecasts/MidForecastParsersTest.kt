package com.project.byeoldori.forecast.utils.forecasts

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * 기상청 중기 육상·기온 예보 텍스트 파서. 공백 구분 컬럼에서 regId/tmFc/tmEf 와 값 컬럼을 뽑는다.
 * 주석(#)·빈 줄은 건너뛰고, 숫자가 아닌 값은 0 으로 둔다.
 */
class MidForecastParsersTest {

    private val land = MidForecastParser()
    private val temp = MidTempForecastParser()

    // 기상청 응답 형식: REG_ID TM_FC TM_EF MOD STN C SKY PRE CONF WF RN_ST
    private val landBody = """
        #START7777
        # REG_ID     TM_FC        TM_EF        MOD STN C  SKY  PRE  CONF WF      RN_ST
        11B10101  202610200600 202610230000 A02  109 1  WB01 WB00 WB40 맑음      10
        11B20201  202610200600 202610231200 A02  109 1  WB03 WB09 WB40 구름많고비 60

        11H20201  202610200600 202610240000 A02  159 1  WB04 WB00 WB40 흐림      abc
        #7777END
    """.trimIndent()

    @Test
    fun `육상예보 - 주석과 빈 줄을 건너뛰고 컬럼을 매핑한다`() {
        val rows = land.parse(landBody)

        assertThat(rows).hasSize(3)
        val seoul = rows[0]
        assertThat(seoul.regId).isEqualTo("11B10101")
        assertThat(seoul.tmFc).isEqualTo("202610200600")
        assertThat(seoul.tmEf).isEqualTo("202610230000")
        assertThat(seoul.sky).isEqualTo("WB01")
        assertThat(seoul.pre).isEqualTo("WB00")
        assertThat(seoul.rnSt).isEqualTo(10)

        assertThat(rows[1].sky).isEqualTo("WB03")
        assertThat(rows[1].pre).isEqualTo("WB09")
        assertThat(rows[1].rnSt).isEqualTo(60)
    }

    @Test
    fun `육상예보 - 강수확률이 숫자가 아니면 0`() {
        val rows = land.parse(landBody)
        assertThat(rows[2].rnSt).isEqualTo(0)
    }

    @Test
    fun `육상예보 - 앞뒤 공백·탭이 섞여도 같은 결과`() {
        val tabbed = "\t 11B10101\t202610200600  202610230000\tA02 109 1 WB01 WB00 WB40 맑음 10   \r\n"
        val rows = land.parse(tabbed)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].regId).isEqualTo("11B10101")
        assertThat(rows[0].rnSt).isEqualTo(10)
    }

    @Test
    fun `육상예보 - 빈 응답·주석만 있는 응답은 빈 목록`() {
        assertThat(land.parse("")).isEmpty()
        assertThat(land.parse("#START7777\n#7777END\n")).isEmpty()
        assertThat(land.parse("\n\n   \n")).isEmpty()
    }

    @Test
    fun `육상예보 - 저장 전 id 는 0 (DB 자동 생성)`() {
        assertThat(land.parse(landBody)).allSatisfy { assertThat(it.id).isEqualTo(0L) }
    }

    // 기상청 응답 형식: REG_ID TM_FC TM_EF MOD STN C MIN MAX MIN_L MIN_H MAX_L MAX_H
    private val tempBody = """
        #START7777
        # REG_ID     TM_FC        TM_EF        MOD STN C  MIN MAX MIN_L MIN_H MAX_L MAX_H
        11B10101  202610200600 202610230000 A02  109 1   5  15  -1  1  -1  1
        11H20201  202610200600 202610230000 A02  159 1  -3   7  -1  1  -1  1
        11C20401  202610200600 202610230000 A02  133 1   -  -   -  -   -  -
        #7777END
    """.trimIndent()

    @Test
    fun `기온예보 - 최저·최고 기온을 정수로 읽고 음수도 처리한다`() {
        val rows = temp.parse(tempBody)

        assertThat(rows).hasSize(3)
        assertThat(rows[0].regId).isEqualTo("11B10101")
        assertThat(rows[0].min).isEqualTo(5)
        assertThat(rows[0].max).isEqualTo(15)
        assertThat(rows[1].min).isEqualTo(-3)
        assertThat(rows[1].max).isEqualTo(7)
    }

    @Test
    fun `기온예보 - 결측 표시(-)는 0`() {
        val rows = temp.parse(tempBody)
        assertThat(rows[2].min).isEqualTo(0)
        assertThat(rows[2].max).isEqualTo(0)
    }

    @Test
    fun `기온예보 - 빈 응답은 빈 목록`() {
        assertThat(temp.parse("")).isEmpty()
        assertThat(temp.parse("# only comment")).isEmpty()
    }

    @Test
    fun `컬럼이 부족한 줄은 현재 IndexOutOfBounds 로 전체 파싱이 실패한다`() {
        // 현재 동작 기록: 기상청이 주석(#) 없는 오류 문구나 짧은 줄을 섞어 보내면 응답 전체를 버린다
        assertThatThrownBy { land.parse("Authentication failed") }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
        assertThatThrownBy { temp.parse("11B10101 202610200600 202610230000") }
            .isInstanceOf(IndexOutOfBoundsException::class.java)
    }

    @Disabled("의심 버그: 컬럼이 부족한 줄 하나가 정상 줄까지 모두 버린다. 해당 줄만 건너뛰는 것이 기대 동작 — PR 본문 참고")
    @Test
    fun `컬럼이 부족한 줄은 건너뛰고 나머지는 파싱해야 한다`() {
        val mixed = "11B10101  202610200600 202610230000 A02 109 1 WB01 WB00 WB40 맑음 10\n" +
            "Service temporarily unavailable\n" +
            "11B20201  202610200600 202610231200 A02 109 1 WB03 WB09 WB40 구름많고비 60\n"
        assertThatCode { land.parse(mixed) }.doesNotThrowAnyException()
        assertThat(land.parse(mixed).map { it.regId }).containsExactly("11B10101", "11B20201")
    }
}
