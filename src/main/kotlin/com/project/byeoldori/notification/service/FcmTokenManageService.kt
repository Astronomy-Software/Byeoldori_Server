package com.project.byeoldori.notification.service

import com.project.byeoldori.notification.entity.FcmToken
import com.project.byeoldori.notification.repository.FcmTokenRepository
import com.project.byeoldori.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FcmTokenManageService(private val fcmTokenRepository: FcmTokenRepository) {

    @Transactional
    fun register(user: User, token: String, deviceType: String = "android") {
        if (!fcmTokenRepository.existsByToken(token)) {
            fcmTokenRepository.save(FcmToken(user = user, token = token, deviceType = deviceType))
        }
    }

    @Transactional
    fun unregister(user: User, token: String) {
        fcmTokenRepository.deleteByUserIdAndToken(user.id, token)
    }

    @Transactional
    fun unregisterAll(userId: Long) {
        fcmTokenRepository.deleteAllByUserId(userId)
    }
}
