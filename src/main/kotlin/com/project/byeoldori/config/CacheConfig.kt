package com.project.byeoldori.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig(private val redisConnectionFactory: RedisConnectionFactory) {

    private fun redisObjectMapper(): ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(kotlinModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .activateDefaultTyping(
            com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Any::class.java)
                .build(),
            ObjectMapper.DefaultTyping.NON_FINAL
        )

    @Bean
    fun cacheManager(): RedisCacheManager {
        val valueSerializer = RedisSerializationContext.SerializationPair
            .fromSerializer(GenericJackson2JsonRedisSerializer(redisObjectMapper()))
        val keySerializer = RedisSerializationContext.SerializationPair
            .fromSerializer(StringRedisSerializer())

        val defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(keySerializer)
            .serializeValuesWith(valueSerializer)
            .disableCachingNullValues()

        val cacheConfigs = mapOf(
            // 중기 예보: 스케줄러 갱신 주기(06:10/18:10) 보다 약간 길게
            "midForecast"    to defaultConfig.entryTtl(Duration.ofMinutes(40)),
            // 광공해: 거의 변하지 않는 데이터 → 24시간
            "lightPollution" to defaultConfig.entryTtl(Duration.ofHours(24)),
            // 사용자 정보: 탈퇴/정지 반영 위해 짧게
            "userCache"      to defaultConfig.entryTtl(Duration.ofMinutes(5)),
        )

        return RedisCacheManager.builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
            .withInitialCacheConfigurations(cacheConfigs)
            .build()
    }
}
