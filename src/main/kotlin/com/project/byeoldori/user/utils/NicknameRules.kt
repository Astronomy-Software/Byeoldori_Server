package com.project.byeoldori.user.utils

object NicknameRules {
    // 웹 온보딩 입력란(maxLength=20)과 같은 상한
    const val MAX_LENGTH = 20

    // 닉네임이 없는 사용자를 다른 사용자에게 보여줄 때 쓰는 이름.
    // name 은 실명(아이디 찾기·본인확인용, 소셜은 provider 실명)이라 알림 등에 대신 내보내지 않는다.
    const val FALLBACK_DISPLAY_NAME = "익명 사용자"

    // 앞뒤 공백을 지우고, 비었으면 null (users.nickname 은 UNIQUE 라 "" 를 저장하면 두 번째 사용자부터 충돌한다)
    fun normalize(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}
