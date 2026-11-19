package com.project.byeoldori.user.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 리프레시 토큰을 회전한 직후 잠깐(기본 30초) 옛 토큰 → 새로 발급한 토큰을 기억한다.
 *
 * 같은 브라우저의 두 탭이 동시에 재발급하면 둘째 요청은 첫째가 회전해 버린 옛 토큰을 들고 와 401 이 되고,
 * 그 탭은 로그아웃된다. 유예 시간 안의 둘째 요청에는 첫째가 이미 발급한 **같은** 새 토큰을 돌려줘(멱등) 두 탭이 같은 세션을 이어 쓴다.
 *
 * - 재사용 탐지(옛 토큰이 쓰이면 세션 폐기)는 원래 없으므로 이 유예로 약해지는 탐지는 없다.
 * - 새 토큰의 세션 행이 지워졌으면(로그아웃·비밀번호 변경) 쓰지 않는다 → 호출부가 행을 다시 확인한다.
 * - 메모리에만 두므로 재시작·블루그린 전환 뒤에는 유예가 사라진다(예전 동작으로 돌아갈 뿐). 크기 상한으로 메모리를 묶는다.
 */
@Component
class RefreshRotationGrace internal constructor(
    private val clock: Clock,
    private val ttl: Duration,
    private val maxEntries: Int,
) {
    // 스프링은 인자 없는 생성자를 쓴다(테스트는 시계·유예 시간을 바꿔 위 생성자로 만든다)
    @Autowired
    constructor() : this(Clock.systemUTC(), Duration.ofSeconds(30), 10_000)

    data class Successor(val refreshToken: String, val userId: Long, val rotatedAt: Instant)

    private val byOldHash = ConcurrentHashMap<String, Successor>()

    fun remember(oldTokenHash: String, successorRefreshToken: String, userId: Long) {
        purgeExpired()
        if (byOldHash.size >= maxEntries) return  // 상한이면 유예 없이(예전 동작)
        byOldHash[oldTokenHash] = Successor(successorRefreshToken, userId, clock.instant())
    }

    /** 유예 시간 안이고 같은 사용자일 때만 후속 토큰을 돌려준다. */
    fun successorOf(oldTokenHash: String, userId: Long): String? {
        val s = byOldHash[oldTokenHash] ?: return null
        if (s.userId != userId || s.rotatedAt.plus(ttl).isBefore(clock.instant())) return null
        return s.refreshToken
    }

    private fun purgeExpired() {
        val cutoff = clock.instant().minus(ttl)
        byOldHash.entries.removeIf { it.value.rotatedAt.isBefore(cutoff) }
    }
}
