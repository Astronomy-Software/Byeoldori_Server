package com.project.byeoldori.user.utils

object EmailMasker {
    // 아이디 찾기 응답용 마스킹. 예: beom710@gmail.com → be***@gm***.com
    // 이름+전화번호만 알면 누구나 호출할 수 있는 API 라 로그인 이메일 원문을 그대로 돌려주지 않는다.
    fun mask(email: String): String {
        val at = email.lastIndexOf('@')
        if (at <= 0 || at == email.length - 1) return "***"
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        val dot = domain.lastIndexOf('.')
        val (host, tld) = if (dot > 0) domain.substring(0, dot) to domain.substring(dot) else domain to ""
        return "${keepHead(local)}***@${keepHead(host)}***$tld"
    }

    // 2글자 이하면 1글자만, 그보다 길면 앞 2글자만 남긴다
    private fun keepHead(s: String): String = s.take(if (s.length <= 2) 1 else 2)
}
