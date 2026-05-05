package com.project.byeoldori.security

import com.project.byeoldori.common.exception.*
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Component
class CurrentUserResolver(
    private val userRepository: UserRepository
) {
    fun getUser(): User {
        val req = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        val attrUser = req?.getAttribute("currentUser") as? User
        if (attrUser?.id != null) {
            val found = userRepository.findByIdOrNull(attrUser.id!!)
                ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=${attrUser.id})")
            return found.assertNotDeleted()
        }

        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException(ErrorCode.LOGIN_REQUIRED.message)

        return when (val p = auth.principal) {
            is User -> {
                val id = p.id
                (userRepository.findByIdOrNull(id)
                    ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=$id)"))
                    .assertNotDeleted()
            }
            is org.springframework.security.core.userdetails.UserDetails ->
                userRepository.findByEmail(p.username).orElseThrow {
                    NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (email=${p.username})")
                }.assertNotDeleted()
            is String ->
                userRepository.findByEmail(p).orElseThrow {
                    NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (email=$p)")
                }.assertNotDeleted()
            is Number -> {
                val id = p.toLong()
                (userRepository.findByIdOrNull(id)
                    ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=$id)"))
                    .assertNotDeleted()
            }
            else -> throw UnauthorizedException("잘못된 인증 컨텍스트입니다.")
        }
    }

    private fun User.assertNotDeleted(): User {
        if (deletedAt != null) throw UnauthorizedException("탈퇴한 계정입니다.")
        return this
    }
}