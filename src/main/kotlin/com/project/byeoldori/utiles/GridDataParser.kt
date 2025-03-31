package com.project.byeoldori.utiles

object GridDataParser {
    // TODO : Utiles 안에 Parser 파일만들어서 Parsing진행하는 친구들 모아주기
    /**
     * 쉼표로 구분된 전체 데이터를 149개씩 그룹화하여 2차원 리스트로 변환합니다.
     * 각 값이 "-99.00"이면 null, 그 외에는 Double로 변환합니다.
     *
     * @param data 전체 데이터 문자열 (쉼표로 구분되어 있음)
     * @return 2차원 리스트 (행의 개수: 253, 열의 개수: 149 예상) - 행 순서는 위아래가 반전됨.
     */
    fun parseGridData(data: String, numCols: Int = 149): MutableList<MutableList<Double?>> {
        val result: MutableList<MutableList<Double?>> = ArrayList()

        // 전체 데이터를 쉼표 기준으로 분리한 후 토큰 리스트 생성
        val tokens = data.split(",".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 전체 토큰 개수를 이용해 행 수 계산 (예: 253행이 되어야 함)
        val totalTokens = tokens.size
        val numRows = totalTokens / numCols

        // 토큰들을 numCols씩 그룹화하여 행을 생성
        for (i in 0 until numRows) {
            val row: MutableList<Double?> = ArrayList()
            val startIndex = i * numCols
            val endIndex = startIndex + numCols
            for (j in startIndex until endIndex) {
                val token = tokens[j]
                if (token == "-99.00") {
                    row.add(null)
                } else {
                    try {
                        row.add(token.toDouble())
                    } catch (e: NumberFormatException) {
                        row.add(null)
                    }
                }
            }
            result.add(row)
        }

        // 행 순서를 반전시켜 맨 위의 행이 원래 맨 아래 행이 되도록 함.
        result.reverse()

        // 검증 메시지 출력
        if (result.size != 253) {
            println("경고: 예상 행의 개수 253개와 다릅니다. 실제 행 개수: ${result.size}")
        }
        result.forEachIndexed { index, row ->
            if (row.size != numCols) {
                println("경고: 행 $index 의 열 개수가 예상 다릅니다. 실제 열 개수: ${row.size}")
            }
        }

        return result
    }

    /**
     * 2차원 리스트 데이터를 심볼로 출력합니다.
     * 각 셀이 null이면 빈 네모("□"), 값이 있으면 채워진 네모("■")로 표시하여 한 줄로 출력합니다.
     *
     * @param gridData 2차원 리스트 데이터
     */
    fun printGridDataSymbols(gridData: MutableList<MutableList<Double?>>) {
        for (row in gridData) {
            val sb = StringBuilder()
            for (cell in row) {
                sb.append(if (cell == null) "□" else "■")
            }
            println(sb.toString())
        }
    }

    // 만약 String이나 다른 데이터값으로 파싱해야하는경우 추가로 작성해줘야함
    // 아래는 String값으로 변환하는것의 예시
    fun parseGridDataString(data: String, numCols: Int = 149): MutableList<MutableList<String?>> {
        val result: MutableList<MutableList<String?>> = ArrayList()

        val tokens = data.split(",".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val totalTokens = tokens.size
        val numRows = totalTokens / numCols

        for (i in 0 until numRows) {
            val row: MutableList<String?> = ArrayList()
            val startIndex = i * numCols
            val endIndex = startIndex + numCols
            for (j in startIndex until endIndex) {
                val token = tokens[j]
                // SKY 데이터는 -99.00일 경우 null로 처리하고, 나머지는 그대로 사용
                if (token == "-99.00") {
                    row.add(null)
                } else {
                    row.add(token)
                }
            }
            result.add(row)
        }
        result.reverse()  // 기존처럼 행 순서를 반전시킴 (필요시)
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        // 예시 데이터 문자열: 실제 데이터는 쉼표로 구분된 37897개 정도의 토큰(253행 * 149열)이 있어야 함.
        // 아래 예시는 간단한 예시이므로 실제 데이터에 맞게 수정 필요.
        val data = ("-99.00, 7.00, 8.00, 9.00, 10.00, " +
                "11.00, 12.00, 13.00, 14.00, 15.00, " +
                "16.00, 17.00, 18.00, -99.00, -99.00, ") // 예시: 실제 데이터는 149개씩 구성된 토큰이어야 함.

        // 여기서는 테스트용으로 data를 149개 항목을 가진 한 행으로 가정하고 복제해 253행을 만듦
        val oneRow = data.trim().removeSuffix(",").split(",").map { it.trim() }
        // 실제 행의 개수를 맞추기 위해 149개의 항목으로 채워야 하므로, 부족한 항목은 "-99.00" 으로 채우기
        val filledRow = mutableListOf<String>().apply {
            addAll(oneRow)
            while (size < 149) add("-99.00")
        }
        // 253행 데이터를 구성
        val allTokens = (1..253).flatMap { filledRow }.joinToString(", ")

        // 데이터 파싱 (행 순서가 반전됨)
        val parsedData = parseGridData(allTokens)
        // 심볼로 출력: null이면 "□", 값이 있으면 "■"
        printGridDataSymbols(parsedData)
    }
}
