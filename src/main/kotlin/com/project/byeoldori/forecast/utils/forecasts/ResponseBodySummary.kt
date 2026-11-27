package com.project.byeoldori.forecast.utils.forecasts

/**
 * 기상청 응답 본문을 로그 한 줄에 넣을 수 있게 줄인다(공백 정리, 앞부분만).
 * 본문에는 인증키가 들어가지 않는다(키는 요청 URL 에만 있음).
 */
object ResponseBodySummary {
    private const val MAX_CHARS = 120

    fun of(body: String?): String {
        val collapsed = body.orEmpty().replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return "(빈 응답)"
        return if (collapsed.length <= MAX_CHARS) collapsed else collapsed.take(MAX_CHARS) + "…"
    }
}
