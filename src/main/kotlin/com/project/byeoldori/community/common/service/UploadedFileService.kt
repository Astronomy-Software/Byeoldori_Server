package com.project.byeoldori.community.common.service

import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.common.exception.UploadQuotaExceededException
import com.project.byeoldori.community.common.domain.UploadedFile
import com.project.byeoldori.community.common.repository.UploadedFileRepository
import com.project.byeoldori.community.post.repository.PostImageRepository
import com.project.byeoldori.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.LocalDateTime

/**
 * 업로드 파일의 소유(업로더) 기록과 물리 삭제 판단을 맡는다.
 *
 * - 업로드: 사용자별 쿼터(시간당 개수·24시간 용량)를 확인하고, 저장된 URL 과 업로더를 기록한다.
 * - 삭제: 게시글·일정에서 빠진 URL 은 "요청자가 올렸고, 어디서도 더 참조하지 않는" 파일만 커밋 후 지운다.
 *   imageUrls 는 클라이언트가 보낸 문자열이라 남의 파일 URL 이 섞일 수 있기 때문이다.
 */
@Service
class UploadedFileService(
    private val repo: UploadedFileRepository,
    private val postImageRepo: PostImageRepository,
    private val calendarImageRepo: CalendarImageRepository,
    private val userRepo: UserRepository,
    private val storage: StorageService,
    @Value("\${storage.upload.max-files-per-hour:120}") private val maxFilesPerHour: Long,
    @Value("\${storage.upload.max-bytes-per-day:524288000}") private val maxBytesPerDay: Long,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /** 업로드 전에 호출한다. 한도를 넘으면 429(UPLOAD_QUOTA_EXCEEDED). */
    @Transactional(readOnly = true)
    fun checkQuota(uploaderId: Long, incomingBytes: Long, now: LocalDateTime = LocalDateTime.now()) {
        if (repo.countByUploaderIdAndCreatedAtAfter(uploaderId, now.minusHours(1)) >= maxFilesPerHour) {
            throw UploadQuotaExceededException("업로드 횟수 한도를 초과했습니다. (시간당 최대 ${maxFilesPerHour}개)")
        }
        val usedBytes = repo.sumSizeBytesSince(uploaderId, now.minusDays(1))
        if (usedBytes + incomingBytes > maxBytesPerDay) {
            throw UploadQuotaExceededException(
                "업로드 용량 한도를 초과했습니다. (24시간 최대 ${maxBytesPerDay / (1024 * 1024)}MB)"
            )
        }
    }

    @Transactional
    fun record(url: String, uploaderId: Long, sizeBytes: Long) {
        repo.save(UploadedFile(url = url, uploaderId = uploaderId, sizeBytes = sizeBytes))
    }

    /**
     * 게시글·일정 이미지 행을 지운 "같은 트랜잭션 안에서" 호출한다.
     * 요청자(actorId)가 올린 파일이고 다른 게시글·일정·프로필이 더는 참조하지 않는 URL 만 골라
     * 기록을 지우고, 실제 파일은 커밋이 성공한 뒤에 지운다(롤백 시 파일만 사라지는 것 방지).
     */
    @Transactional
    fun deleteOwnedUnreferencedAfterCommit(urls: Collection<String>, actorId: Long) {
        val deletable = urls.distinct().mapNotNull { url ->
            val record = repo.findByUrl(url)
            when {
                record == null -> null // 기록 없는 파일(외부 URL·V5 이전 업로드)은 지우지 않는다
                record.uploaderId != actorId -> {
                    logger.warn("본인이 올리지 않은 파일이라 삭제를 건너뜁니다 (actor={}, url={})", actorId, url)
                    null
                }
                isStillReferenced(url) -> null
                else -> record
            }
        }
        if (deletable.isEmpty()) return

        repo.deleteAll(deletable)
        val targets = deletable.map { it.url }
        val physicalDelete = {
            targets.forEach { url ->
                try { storage.deleteImageByUrl(url) } catch (e: Exception) {
                    logger.warn("스토리지 파일 삭제 실패 (URL: {}): {}", url, e.message)
                }
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = physicalDelete()
            })
        } else {
            physicalDelete()
        }
    }

    private fun isStillReferenced(url: String): Boolean =
        postImageRepo.existsByUrl(url) ||
            calendarImageRepo.existsByUrl(url) ||
            userRepo.existsByProfileImageUrl(url)
}
