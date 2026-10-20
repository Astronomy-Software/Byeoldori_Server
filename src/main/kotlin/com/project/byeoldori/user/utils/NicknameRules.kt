package com.project.byeoldori.user.utils

object NicknameRules {
    // 웹 온보딩 입력란(maxLength=20)과 같은 상한
    const val MAX_LENGTH = 20

    // 앞뒤 공백을 지우고, 비었으면 null (users.nickname 은 UNIQUE 라 "" 를 저장하면 두 번째 사용자부터 충돌한다)
    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}
