package com.project.byeoldori.forecast.snapshot

import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStringCommands
import org.springframework.data.redis.core.types.Expiration
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant

/** 스냅샷 바이트를 담는 저장소(운영은 Redis). 테스트에서 메모리·장애 구현으로 바꿔 끼운다. */
interface SnapshotBlobStore {
    fun put(key: String, value: ByteArray, ttl: Duration)
    fun get(key: String): ByteArray?
}

@Component
class RedisSnapshotBlobStore(
    private val connectionFactory: RedisConnectionFactory,
) : SnapshotBlobStore {
    override fun put(key: String, value: ByteArray, ttl: Duration) {
        connectionFactory.connection.use {
            it.stringCommands().set(key.toByteArray(), value, Expiration.from(ttl), RedisStringCommands.SetOption.upsert())
        }
    }

    override fun get(key: String): ByteArray? =
        connectionFactory.connection.use { it.stringCommands().get(key.toByteArray()) }
}

/**
 * 예보 캐시 스냅샷 저장·복원. Redis 장애나 형식 불일치는 예외로 올리지 않고
 * 저장은 건너뛰고(경고), 복원은 null 을 돌려 기존처럼 기상청 경로로 동작하게 한다.
 */
@Component
class ForecastSnapshotStore(
    private val blobs: SnapshotBlobStore,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun <C> save(kind: ForecastSnapshotKind, tmfc: String, slots: Map<String, List<List<C>>>, codec: GridCellCodec<C>, complete: Boolean = true) {
        try {
            val bytes = ForecastSnapshotCodec.encode(kind, tmfc, Instant.now(), slots, codec, complete)
            blobs.put(kind.redisKey, bytes, kind.ttl)
            logger.info("[스냅샷] {} 저장 (tmfc={}, slot={}, complete={}, {}KB)", kind, tmfc, slots.size, complete, bytes.size / 1024)
        } catch (e: Exception) {
            logger.warn("[스냅샷] {} 저장 실패, 메모리 캐시만 갱신합니다: {}", kind, e.message)
        }
    }

    /** 직렬화·Redis 쓰기를 이벤트 루프 밖(boundedElastic)에서 한다. 실패해도 오류를 내지 않는다. */
    fun <C> saveAsync(
        kind: ForecastSnapshotKind, tmfc: String, slots: Map<String, List<List<C>>>, codec: GridCellCodec<C>, complete: Boolean = true,
    ): Mono<Void> =
        Mono.fromRunnable<Void> { save(kind, tmfc, slots, codec, complete) }
            .subscribeOn(Schedulers.boundedElastic())

    fun <C> load(kind: ForecastSnapshotKind, codec: GridCellCodec<C>): ForecastSnapshot<C>? {
        return try {
            val bytes = blobs.get(kind.redisKey) ?: return null
            ForecastSnapshotCodec.decode(bytes, kind, codec).also {
                if (it == null) logger.warn("[스냅샷] {} 형식 버전이 달라 무시합니다", kind)
            }
        } catch (e: Exception) {
            logger.warn("[스냅샷] {} 읽기 실패, 기상청에서 받습니다: {}", kind, e.message)
            null
        }
    }
}
