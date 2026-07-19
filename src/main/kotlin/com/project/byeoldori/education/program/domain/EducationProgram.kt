package com.project.byeoldori.education.program.domain

import com.project.byeoldori.community.common.domain.EducationDifficulty
import org.bson.Document
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument
import java.time.LocalDateTime

enum class ProgramStatus { DRAFT, PREVIEW, PUBLISHED }

/**
 * 교육 프로그램(인터랙티브 씬 JSON) — MongoDB 저장.
 * steps 는 프론트가 보내는 유연/중첩 구조를 org.bson.Document 로 그대로 보존해 재생용으로 반환한다.
 * (MySQL 교육 게시글 EducationPost 와는 완전히 별개 도메인)
 */
@MongoDocument("education_programs")
class EducationProgram(
    @Id
    var id: String? = null,

    var title: String,

    var subtitle: String? = null,

    var difficulty: EducationDifficulty? = null,

    var targets: List<String> = emptyList(),

    var authorId: Long,

    var authorName: String? = null,

    var status: ProgramStatus = ProgramStatus.DRAFT,

    var schemaVersion: Int = 1,

    var steps: List<Document> = emptyList(),

    var viewCount: Long = 0,

    @CreatedDate
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    var updatedAt: LocalDateTime? = null
)
