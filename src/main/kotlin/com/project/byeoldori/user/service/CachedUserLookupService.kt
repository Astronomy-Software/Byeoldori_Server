package com.project.byeoldori.user.service

import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service

@Service
class CachedUserLookupService(private val userRepository: UserRepository) {

    // 예전에는 @Cacheable findIdByEmail 을 같은 클래스에서 this 로 불러(자기 호출) 캐시가 한 번도 적용되지 않았고,
    // 요청마다 email 조회 + PK 조회가 모두 나갔다. 탈퇴·권한 변경이 즉시 반영되도록 캐시 없이 한 번만 조회한다.
    fun findByEmail(email: String): User? =
        userRepository.findByEmail(email).orElse(null)

    // 지금은 캐시에 쓰지 않지만, 이전 배포가 남긴 userCache 항목을 지우는 용도로 호출부는 유지한다
    @CacheEvict("userCache", key = "#email")
    fun evictByEmail(email: String) {}
}
