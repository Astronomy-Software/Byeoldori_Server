package com.project.byeoldori.community.common.domain

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * /files/image, /files/upload 로 올라온 파일의 업로더 기록.
 *
 * 게시글·일정의 imageUrls 는 클라이언트가 보내는 문자열이라, 이 기록 없이 물리 삭제하면
 * 남의 공개 파일 URL 을 내 글에 붙였다가 지우는 것만으로 그 파일이 디스크에서 사라진다(IDOR).
 * 그래서 물리 삭제는 "요청자가 올린 파일" 일 때만 허용하고, 사용자별 업로드 쿼터도 이 표로 센다.
 * 기록이 없는 파일(V5 이전 업로드·외부 URL)은 지우지 않는다(고아로 남아도 남의 파일을 지우는 것보다 낫다).
 */
@Entity
@Table(
    name = "uploaded_file",
    uniqueConstraints = [UniqueConstraint(name = "uk_uploaded_file_url", columnNames = ["url"])],
    indexes = [Index(name = "idx_uploaded_file_uploader_created", columnList = "uploader_id, created_at")]
)
class UploadedFile(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 512)
    val url: String,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: Long,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
