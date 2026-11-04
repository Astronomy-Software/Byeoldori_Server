package com.project.byeoldori.forecast.api

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
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
 * 주기를 넘어 공유하는 기상청 도달 상태(실황·초단기·단기 공용, half-open 프로브).
 * 직전 주기가 연결 단계 실패로 끝났으면 다음 주기는 요청 1건으로 먼저 떠보고, 그것도 연결 단계에서 실패하면
 * 나머지 요청을 보내지 않고 바로 [KmaUnreachableException] 으로 끝낸다(VM IP 가 막힌 동안 주기당 1건만 시도).
 * 어디서든 기상청 응답을 한 번이라도 받으면 상태를 지운다.
 */
@Component
class KmaReachability {
    private val unreachable = AtomicBoolean(false)

    val isUnreachable: Boolean
        get() = unreachable.get()

    fun onSuccess() = unreachable.set(false)

    /** 주기가 연결 단계 실패로 끝났을 때만 표시한다(응답 지연·HTTP 오류는 기상청이 살아 있으므로 표시하지 않음). */
    fun onCycleError(error: Throwable) {
        if (KmaFailures.isNetworkFailure(error)) unreachable.set(true)
    }

    /**
     * 막힌 상태가 아니면 [cycle] 을 그대로 실행한다.
     * 막힌 상태면 [probe] 1건을 먼저 보내 연결 단계 실패면 [cycle] 없이 오류로 끝내고,
     * 응답을 받았거나(성공) 연결은 된 실패(응답 지연·HTTP 오류)면 상태를 지우고 [cycle] 을 실행한다.
     */
    fun <T : Any> probeThen(tag: String, probe: () -> Mono<*>, cycle: () -> Mono<T>): Mono<T> = Mono.defer {
        if (!unreachable.get()) return@defer cycle()
        Mono.defer(probe)
            .then(Mono.fromRunnable<Void> { onSuccess() })
            .onErrorResume { e ->
                if (KmaFailures.isNetworkFailure(e)) {
                    Mono.error(KmaUnreachableException("$tag: 기상청 연결 확인(프로브) 실패, 이번 주기 건너뜀 (${e.message})"))
                } else {
                    onSuccess()
                    Mono.empty()
                }
            }
            .then(Mono.defer(cycle))
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
