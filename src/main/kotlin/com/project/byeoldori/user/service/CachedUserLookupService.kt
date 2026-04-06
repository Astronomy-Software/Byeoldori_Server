package com.project.byeoldori.user.service

import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CachedUserLookupService(private val userRepository: UserRepository) {

    // JPA 엔티티 직렬화 이슈를 피하기 위해 userId(Long)만 캐싱하고 User는 PK 조회
    @Cacheable("userCache", key = "#email")
    fun findIdByEmail(email: String): Long? =
        userRepository.findByEmail(email).orElse(null)?.id

    fun findByEmail(email: String): User? {
        val id = findIdByEmail(email) ?: return null
        return userRepository.findById(id).orElse(null)
    }

    @CacheEvict("userCache", key = "#email")
    fun evictByEmail(email: String) {}
}
