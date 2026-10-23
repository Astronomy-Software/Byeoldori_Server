package com.project.byeoldori.forecast.api

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** 기상청에 닿지 못해 이번 주기를 중단했을 때 낸다(스케줄러가 간격을 늘려 재시도한다). */
class KmaUnreachableException(message: String) : RuntimeException(message)

object KmaFailures {
    /**
     * 연결 단계(연결 거부·연결 시간 초과·경로 없음·DNS)에서 실패했는지. WebClientRequestException 은 원인 사슬로 판단한다.
     * 연결은 됐는데 응답이 느린 경우(ReadTimeout·responseTimeout)는 기상청이 살아 있는 것이므로 해당하지 않고,
     * 기상청이 HTTP 오류 상태를 돌려준 경우(WebClientResponseException)도 해당하지 않는다.
     */
    fun isNetworkFailure(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth < 10) {
            when (e) {
                is KmaUnreachableException,
                is ConnectException,                       // netty ConnectTimeoutException 도 ConnectException 의 하위 타입
                is io.netty.channel.ConnectTimeoutException,
                is NoRouteToHostException,
                is UnknownHostException -> return true
            }
            e = e.cause.takeIf { it !== e }
            depth++
        }
        return false
    }
}

/**
 * 한 번의 적재 주기 안에서 쓰는 회로 차단기.
 * 이번 주기에 아직 성공한 tmef 가 하나도 없을 때 연결 단계 실패가 연속 [threshold] 번 나면 열려,
 * 남은 tmef 는 호출하지 않고 건너뛴다(VM IP 가 막혔을 때 수백 번 연결 시도를 쌓지 않게).
 * 한 번이라도 성공하면 기상청에 닿는 것이므로 그 주기에서는 더 이상 열리지 않는다(실패한 tmef 만 건너뛰고 부분 결과를 반영).
 */
class KmaCallCircuit(private val threshold: Int = DEFAULT_THRESHOLD) {
    private val consecutiveFailures = AtomicInteger(0)
    private val anySucceeded = AtomicBoolean(false)
    private val tripped = AtomicBoolean(false)

    val isOpen: Boolean
        get() = tripped.get() && !anySucceeded.get()

    fun onSuccess() {
        anySucceeded.set(true)
        consecutiveFailures.set(0)
    }

    fun onFailure(error: Throwable) {
        if (anySucceeded.get()) return
        if (KmaFailures.isNetworkFailure(error) && consecutiveFailures.incrementAndGet() >= threshold) {
            tripped.set(true)
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3
    }
}
