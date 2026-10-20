package com.project.byeoldori.user.utils

object DeletedAccount {
    // 탈퇴 계정 전용 내부 도메인. 실제로 메일을 받을 수 없으므로 가입 이메일로는 쓸 수 없다.
    const val EMAIL_DOMAIN = "deleted.byeoldori"
    const val ANONYMOUS_NAME = "탈퇴한 사용자"

    // 탈퇴하면 email(UNIQUE)을 이 값으로 바꿔 원래 이메일로 재가입·소셜 로그인이 가능하게 한다.
    // V4__anonymize_deleted_users.sql 이 기존 탈퇴 계정에 같은 형식을 적용한다.
    fun tombstoneEmail(userId: Long): String = "deleted+$userId@$EMAIL_DOMAIN"
}
