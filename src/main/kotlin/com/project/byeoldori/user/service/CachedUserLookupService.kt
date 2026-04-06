package com.project.byeoldori.user.service

import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CachedUserLookupService(private val userRepository: UserRepository) {

    @Cacheable("userCache", key = "#email")
    fun findByEmail(email: String): User? =
        userRepository.findByEmail(email).orElse(null)

    @CacheEvict("userCache", key = "#email")
    fun evictByEmail(email: String) {}
}
