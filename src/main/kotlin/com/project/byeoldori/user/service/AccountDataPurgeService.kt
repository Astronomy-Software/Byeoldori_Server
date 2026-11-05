package com.project.byeoldori.user.service

import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.community.common.service.UploadedFileService
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 회원 탈퇴 시 본인만 보던 개인 데이터를 지운다(개인정보처리방침: 탈퇴 즉시 파기).
 * 공개 게시글·댓글·좋아요·평점은 "탈퇴한 사용자"로 남기는 설계라 여기서 건드리지 않는다.
 *
 * - 관측 일정(observation_event: 제목·메모·위치)과 대상 천체 태그(content_target EVENT), 일정 사진(calendar_image).
 *   사진 파일은 CalendarService.delete 와 같은 규칙으로, 본인이 올렸고 더는 참조되지 않는 것만 커밋 후 지운다.
 * - 즐겨찾기 관측지(user_saved_sites: 직접 지정한 좌표·이름), 받은 알림(notifications).
 * - 교육 퀴즈 응시 기록(MongoDB). JPA 트랜잭션에 묶이지 않으므로 커밋이 성공한 뒤에 지운다
 *   (롤백돼 계정이 살아 있는데 기록만 사라지는 일 방지).
 *
 * 이미 탈퇴한 계정의 MySQL 데이터는 V7__purge_deleted_users_private_data.sql 이 한 번 정리한다.
 */
@Service
class AccountDataPurgeService(
    private val events: ObservationEventRepository,
    private val photos: CalendarImageRepository,
    private val contentTargets: ContentTargetRepository,
    private val uploadedFiles: UploadedFileService,
    private val savedSites: UserSavedSiteRepository,
    private val notifications: NotificationRepository,
    private val quizAttempts: QuizAttemptRepository,
) {
    private val log = LoggerFactory.getLogger(AccountDataPurgeService::class.java)

    @Transactional
    fun purgePrivateData(userId: Long) {
        val userEvents = events.findAllByUserId(userId)
        val eventIds = userEvents.mapNotNull { it.id }
        if (eventIds.isNotEmpty()) {
            val eventPhotos = photos.findAllByEventIdIn(eventIds)
            contentTargets.deleteAllByContentIds(ContentType.EVENT, eventIds)
            if (eventPhotos.isNotEmpty()) photos.deleteAll(eventPhotos)
            events.deleteAll(userEvents)
            // 사진 행을 지운 같은 트랜잭션 안에서 호출해야 "더는 참조되지 않음" 판단이 맞다
            uploadedFiles.deleteOwnedUnreferencedAfterCommit(eventPhotos.map { it.url }, userId)
        }
        savedSites.deleteAllByUserId(userId)
        notifications.deleteAllByUserId(userId)
        deleteQuizAttemptsAfterCommit(userId)
    }

    private fun deleteQuizAttemptsAfterCommit(userId: Long) {
        val delete = {
            try {
                quizAttempts.deleteByUserId(userId)
            } catch (e: Exception) {
                log.warn("탈퇴 회원 퀴즈 응시 기록 삭제 실패 (userId={}): {}", userId, e.message)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = delete()
            })
        } else {
            delete()
        }
    }
}
