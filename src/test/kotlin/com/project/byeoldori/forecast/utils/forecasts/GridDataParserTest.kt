package com.project.byeoldori.forecast.utils.forecasts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * GridDataParser 순수 파싱 로직 검증 (Spring 컨텍스트 불필요).
 * 규칙: numCols 개씩 행 분할, 행 순서 반전, "-99" 접두 토큰은 null, 파싱 실패도 null.
 */
class GridDataParserTest {

    @Test
    fun `numCols 단위로 그룹화하고 행 순서를 반전한다`() {
        // 토큰: [1,2,3,4] numCols=2 → 원본 [[1,2],[3,4]] → 반전 [[3,4],[1,2]]
        val result = GridDataParser.parseGridData("1,2,3,4", numCols = 2)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(3.0, 4.0)
        assertThat(result[1]).containsExactly(1.0, 2.0)
    }

    @Test
    fun `-99 접두 토큰은 null로 변환된다`() {
        // [1.5, -99.00, 3, 4] numCols=2 → [[1.5,null],[3,4]] → 반전 [[3,4],[1.5,null]]
        val result = GridDataParser.parseGridData("1.5,-99.00,3,4", numCols = 2)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(3.0, 4.0)
        assertThat(result[1]).containsExactly(1.5, null)
    }

    @Test
    fun `공백은 트림되고 빈 토큰은 무시된다`() {
        val result = GridDataParser.parseGridData(" 1 , 2 , , 3 , 4 ", numCols = 2)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly(3.0, 4.0)
        assertThat(result[1]).containsExactly(1.0, 2.0)
    }

    @Test
    fun `숫자로 변환할 수 없는 토큰은 null이 된다`() {
        val result = GridDataParser.parseGridData("abc,2", numCols = 2)

        assertThat(result).hasSize(1)
        assertThat(result[0]).containsExactly(null, 2.0)
    }

    @Test
    fun `행 수는 전체 토큰수를 numCols로 나눈 몫이며 나머지는 버려진다`() {
        // 5개 토큰, numCols=2 → 2행(4토큰), 마지막 토큰은 버려짐
        val result = GridDataParser.parseGridData("1,2,3,4,5", numCols = 2)

        assertThat(result).hasSize(2)
        assertThat(result.flatten()).containsExactly(3.0, 4.0, 1.0, 2.0)
    }

    @Test
    fun `문자열 파서는 정확히 -99_00 만 null 처리하고 나머지는 원문을 유지한다`() {
        // [A,-99.00,-99,B] numCols=2 → [[A,null],[-99,B]] → 반전 [[-99,B],[A,null]]
        val result = GridDataParser.parseGridDataString("A,-99.00,-99,B", numCols = 2)

        assertThat(result).hasSize(2)
        assertThat(result[0]).containsExactly("-99", "B")
        assertThat(result[1]).containsExactly("A", null)
    }
}
