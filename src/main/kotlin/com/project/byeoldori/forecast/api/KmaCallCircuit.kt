package com.project.byeoldori.forecast.api

import org.springframework.web.reactive.function.client.WebClientRequestException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/** 기상청에 닿지 못해 이번 주기를 중단했을 때 낸다(스케줄러가 간격을 늘려 재시도한다). */
class KmaUnreachableException(message: String) : RuntimeException(message)

object KmaFailures {
    /**
     * 응답을 받기 전 네트워크 단계(연결 거부·연결/응답 시간 초과·DNS)에서 실패했는지.
     * 기상청이 HTTP 오류 상태를 돌려준 경우(WebClientResponseException)는 해당하지 않는다.
     */
    fun isNetworkFailure(error: Throwable?): Boolean {
        var e = error
        var depth = 0
        while (e != null && depth < 10) {
            when (e) {
                is KmaUnreachableException,
                is WebClientRequestException,
                is ConnectException,
                is SocketTimeoutException,
                is UnknownHostException,
                is TimeoutException,
                is io.netty.channel.ConnectTimeoutException,
                is io.netty.handler.timeout.TimeoutException -> return true
            }
            e = e.cause.takeIf { it !== e }
            depth++
        }
        return false
    }
}

/**
 * 한 번의 적재 주기 안에서 쓰는 회로 차단기.
 * 네트워크 단계 실패가 연속 [threshold] 번 나면 열려, 남은 tmef 는 호출하지 않고 건너뛴다
 * (VM IP 가 막혔을 때 수백 번 연결 시도를 쌓지 않게). 성공하면 연속 횟수를 0으로 되돌린다.
 */
class KmaCallCircuit(private val threshold: Int = DEFAULT_THRESHOLD) {
    private val consecutiveFailures = AtomicInteger(0)

    @Volatile
    var isOpen: Boolean = false
        private set

    fun onSuccess() {
        consecutiveFailures.set(0)
    }

    fun onFailure(error: Throwable) {
        if (KmaFailures.isNetworkFailure(error) && consecutiveFailures.incrementAndGet() >= threshold) {
            isOpen = true
        }
    }

    companion object {
        const val DEFAULT_THRESHOLD = 3
    }
}
