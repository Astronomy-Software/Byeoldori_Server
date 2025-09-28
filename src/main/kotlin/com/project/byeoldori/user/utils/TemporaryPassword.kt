package com.project.byeoldori.user.utils

import java.security.SecureRandom


object TemporaryPassword {
    private val random = SecureRandom()
    private const val LOWER = "abcdefghjkmnpqrstuvwxyz" // i,l,o 제외
    private const val UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val DIGIT = "23456789" // 0,1 제외
    private const val SYMBOL = "!@#\$%^&*()-_=+" // 메일 호환 무난한 범위
    private val ALL = (LOWER + UPPER + DIGIT + SYMBOL).toCharArray()


    fun generate(length: Int = 12): String {
        require(length >= 8) { "임시 비밀번호 길이는 8자 이상이어야 합니다." }
        val chars = mutableListOf<Char>()
        chars += LOWER[random.nextInt(LOWER.length)]
        chars += UPPER[random.nextInt(UPPER.length)]
        chars += DIGIT[random.nextInt(DIGIT.length)]
        chars += SYMBOL[random.nextInt(SYMBOL.length)]
        while (chars.size < length) chars += ALL[random.nextInt(ALL.size)]
// Fisher–Yates shuffle
        for (i in chars.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.joinToString("")
    }
}