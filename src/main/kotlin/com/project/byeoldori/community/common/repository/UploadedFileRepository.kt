package com.project.byeoldori.community.common.repository

import com.project.byeoldori.community.common.domain.UploadedFile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UploadedFileRepository : JpaRepository<UploadedFile, Long> {
    fun findByUrl(url: String): UploadedFile?

    fun countByUploaderIdAndCreatedAtAfter(uploaderId: Long, since: LocalDateTime): Long

    @Query("select coalesce(sum(f.sizeBytes), 0) from UploadedFile f where f.uploaderId = :uploaderId and f.createdAt > :since")
    fun sumSizeBytesSince(@Param("uploaderId") uploaderId: Long, @Param("since") since: LocalDateTime): Long
}
