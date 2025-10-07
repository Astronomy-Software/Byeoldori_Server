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
            userRepository.findByIdOrNull(attrUser.id!!)?.let { return it }
            throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=${attrUser.id})")
        }

        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException(ErrorCode.LOGIN_REQUIRED.message)

        return when (val p = auth.principal) {
            is User -> {
                val id = p.id
                userRepository.findByIdOrNull(id)
                    ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=$id)")
            }
            is org.springframework.security.core.userdetails.UserDetails ->
                userRepository.findByEmail(p.username).orElseThrow {
                    NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (email=${p.username})")
                }
            is String ->
                userRepository.findByEmail(p).orElseThrow {
                    NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (email=$p)")
                }
            is Number -> {
                val id = p.toLong()
                userRepository.findByIdOrNull(id)
                    ?: throw NotFoundException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다. (id=$id)")
            }
            else -> throw UnauthorizedException("잘못된 인증 컨텍스트입니다.")
        }
    }
}