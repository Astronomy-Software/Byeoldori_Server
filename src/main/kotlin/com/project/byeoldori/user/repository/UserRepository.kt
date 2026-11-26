package com.project.byeoldori.user.repository

import com.project.byeoldori.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): Optional<User>
    fun findAllByNameAndPhone(name: String, phone: String): List<User>
    fun existsByEmail(email: String): Boolean
    fun existsByNickname(nickname: String): Boolean
    fun existsByProfileImageUrl(profileImageUrl: String): Boolean

    fun findByProviderAndProviderId(provider: String, providerId: String): User?

    /**
     * 버려진 미인증 가입을 새 가입 정보로 덮어쓴다. 읽을 때의 비밀번호 해시가 그대로이고 여전히 '버려진 가입' 상태일 때만 바꾼다(조건부 UPDATE).
     * 같은 이메일로 가입이 동시에 두 번 들어오면 둘째 UPDATE 는 첫째가 커밋할 때까지 기다렸다가 해시가 바뀐 것을 보고 0 건이 된다
     * → 호출부가 409. (그대로 덮어쓰면 남의 비밀번호로 덮인 계정이 이메일 주인의 인증 링크로 인증될 수 있었다)
     */
    @Modifying
    @Query(
        "update User u set u.passwordHash = :newHash, u.name = :name, u.phone = :phone, u.nickname = :nickname, u.birthdate = null " +
            "where u.id = :id and u.passwordHash = :expectedHash and u.provider is null and u.emailVerified = false " +
            "and u.lastLoginAt is null and u.deletedAt is null"
    )
    fun overwriteAbandonedSignup(
        @Param("id") id: Long,
        @Param("expectedHash") expectedHash: String,
        @Param("newHash") newHash: String,
        @Param("name") name: String,
        @Param("phone") phone: String,
        @Param("nickname") nickname: String?,
    ): Int
}