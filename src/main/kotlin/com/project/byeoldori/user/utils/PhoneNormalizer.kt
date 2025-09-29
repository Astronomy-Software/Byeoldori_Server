package com.project.byeoldori.user.utils

object PhoneNormalizer {

    //숫자만 비교 + 대한민국 국가코드(+82) 관대 처리 (예: 82xxxxxxxxxx → 0xxxxxxxxxx)
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var d = raw.filter { it.isDigit() }
        if (d.startsWith("82") && d.length >= 11) d = "0" + d.removePrefix("82")
        return d
    }
}