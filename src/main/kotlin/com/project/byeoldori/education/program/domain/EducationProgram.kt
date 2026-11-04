package com.project.byeoldori.education.program.domain

import com.project.byeoldori.community.common.domain.EducationDifficulty
import org.bson.Document
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document as MongoDocument
import java.time.LocalDateTime

enum class ProgramStatus { DRAFT, PREVIEW, PUBLISHED }

/**
 * 교육 프로그램(인터랙티브 씬 JSON) — MongoDB 저장.
 * steps 는 프론트가 보내는 유연/중첩 구조를 org.bson.Document 로 그대로 보존해 재생용으로 반환한다.
 * (MySQL 교육 게시글 EducationPost 와는 완전히 별개 도메인)
 *
 * 목록은 status·authorId 로 거르고 updatedAt 최신순으로 정렬한다. 인덱스가 없으면 컬렉션 스캔과
 * 메모리 정렬이 되므로 복합 인덱스를 둔다(spring.data.mongodb.auto-index-creation=true 로 기동 시 생성).
 * 이름은 지정하지 않는다 — 같은 키의 인덱스가 Mongo 기본 이름으로 이미 있으면 이름 충돌로 기동이 실패할 수 있다.
 */
@MongoDocument("education_programs")
@CompoundIndexes(
    CompoundIndex(def = "{'status': 1, 'updatedAt': -1}"),
    CompoundIndex(def = "{'authorId': 1, 'updatedAt': -1}")
)
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

    // 관리자가 반려(PREVIEW → DRAFT)할 때 남긴 사유. 재검수 요청·발행 시 비운다.
    var rejectReason: String? = null,

    @CreatedDate
    var createdAt: LocalDateTime? = null,

    @LastModifiedDate
    var updatedAt: LocalDateTime? = null
)
