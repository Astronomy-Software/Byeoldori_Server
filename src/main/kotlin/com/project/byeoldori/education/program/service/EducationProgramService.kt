package com.project.byeoldori.education.program.service

import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.common.dto.toPageResponse
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.dto.CreateProgramRequest
import com.project.byeoldori.education.program.dto.ProgramDetailResponse
import com.project.byeoldori.education.program.dto.ProgramSummaryResponse
import com.project.byeoldori.education.program.dto.UpdateProgramRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.user.entity.User
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service

@Service
class EducationProgramService(
    private val repo: EducationProgramRepository,
    private val systemConfigService: SystemConfigService,
    private val mongoTemplate: MongoTemplate
) {
    private fun isAdmin(user: User): Boolean = user.roles.contains("ADMIN")

    private fun findOrThrow(id: String): EducationProgram =
        repo.findById(id).orElseThrow { NotFoundException(ErrorCode.EDU_PROGRAM_NOT_FOUND) }

    fun create(user: User, req: CreateProgramRequest): ProgramDetailResponse {
        val program = EducationProgram(
            title = req.title,
            subtitle = req.subtitle,
            difficulty = req.difficulty,
            targets = req.targets ?: emptyList(),
            authorId = user.id,
            authorName = user.nickname,
            status = ProgramStatus.DRAFT,
            steps = req.steps?.map { org.bson.Document(it) } ?: emptyList()
        )
        return ProgramDetailResponse.from(repo.save(program))
    }

    fun update(id: String, req: UpdateProgramRequest, user: User): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.authorId != user.id) throw ForbiddenException()
        if (p.status != ProgramStatus.DRAFT && p.status != ProgramStatus.PREVIEW) {
            throw InvalidInputException("발행되었거나 검수 중이 아닌 상태에서만 수정할 수 있습니다.")
        }

        req.title?.let { p.title = it }
        req.subtitle?.let { p.subtitle = it }
        req.difficulty?.let { p.difficulty = it }
        req.targets?.let { p.targets = it }
        req.steps?.let { list -> p.steps = list.map { org.bson.Document(it) } }

        return ProgramDetailResponse.from(repo.save(p))
    }

    /** DRAFT → PREVIEW (작성자 본인) */
    fun submit(id: String, user: User): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.authorId != user.id) throw ForbiddenException()
        if (p.status != ProgramStatus.DRAFT) {
            throw InvalidInputException("작성 중(DRAFT) 상태에서만 검수 요청을 할 수 있습니다.")
        }
        p.status = ProgramStatus.PREVIEW
        return ProgramDetailResponse.from(repo.save(p))
    }

    /**
     * PREVIEW → PUBLISHED.
     * moderation.required(전역) 가 true 면 ADMIN 만, false 면 작성자 본인도 발행 가능.
     */
    fun publish(id: String, user: User): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.status != ProgramStatus.PREVIEW) {
            throw InvalidInputException("검수 대기(PREVIEW) 상태에서만 발행할 수 있습니다.")
        }

        val moderationRequired = systemConfigService.moderationRequired()
        val allowed = if (moderationRequired) {
            isAdmin(user)
        } else {
            isAdmin(user) || p.authorId == user.id
        }
        if (!allowed) throw ForbiddenException("발행 권한이 없습니다.")

        p.status = ProgramStatus.PUBLISHED
        return ProgramDetailResponse.from(repo.save(p))
    }

    /** PREVIEW → DRAFT (ADMIN 반려) */
    fun reject(id: String, user: User): ProgramDetailResponse {
        if (!isAdmin(user)) throw ForbiddenException("관리자만 반려할 수 있습니다.")
        val p = findOrThrow(id)
        if (p.status != ProgramStatus.PREVIEW) {
            throw InvalidInputException("검수 대기(PREVIEW) 상태에서만 반려할 수 있습니다.")
        }
        p.status = ProgramStatus.DRAFT
        return ProgramDetailResponse.from(repo.save(p))
    }

    /** PUBLISHED 전체 공개 목록 */
    fun listPublished(pageable: Pageable): PageResponse<ProgramSummaryResponse> =
        repo.findByStatus(ProgramStatus.PUBLISHED, pageable)
            .map { ProgramSummaryResponse.from(it) }
            .toPageResponse()

    /** 작성자 본인의 전체 상태 목록 */
    fun listMine(user: User, pageable: Pageable): PageResponse<ProgramSummaryResponse> =
        repo.findByAuthorId(user.id, pageable)
            .map { ProgramSummaryResponse.from(it) }
            .toPageResponse()

    /** ADMIN 검수 큐(PREVIEW) 목록 */
    fun listPending(user: User, pageable: Pageable): PageResponse<ProgramSummaryResponse> {
        if (!isAdmin(user)) throw ForbiddenException("관리자만 검수 큐를 조회할 수 있습니다.")
        return repo.findByStatus(ProgramStatus.PREVIEW, pageable)
            .map { ProgramSummaryResponse.from(it) }
            .toPageResponse()
    }

    /** PUBLISHED 는 비로그인 포함 누구나. DRAFT/PREVIEW 는 작성자 또는 관리자만. */
    fun get(id: String, user: User?): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.status != ProgramStatus.PUBLISHED) {
            val allowed = user != null && (p.authorId == user.id || isAdmin(user))
            if (!allowed) throw ForbiddenException("해당 프로그램을 조회할 권한이 없습니다.")
        }
        return ProgramDetailResponse.from(p)
    }

    fun delete(id: String, user: User) {
        val p = findOrThrow(id)
        if (p.authorId != user.id && !isAdmin(user)) throw ForbiddenException()
        repo.delete(p)
    }

    /**
     * 조회수 증가 — 반드시 원자적 부분 갱신으로 처리한다.
     *
     * 예전에는 문서를 통째로 읽어 save() 했는데, 이 API 는 비로그인 포함 누구나
     * 재생할 때마다 호출된다. 저작자가 편집 중이면 (읽기 → 저작자 저장 → 여기서 save)
     * 순서로 방금 저장한 steps 가 통째로 덮어써져 조용히 사라졌다.
     * $inc 만 보내면 다른 필드는 건드리지 않는다.
     *
     * PUBLISHED 만 카운트해 미발행 프로그램의 조회수 조작과 불필요한 쓰기를 막는다.
     */
    fun incrementView(id: String) {
        mongoTemplate.updateFirst(
            Query(
                Criteria.where("_id").`is`(id)
                    .and("status").`is`(ProgramStatus.PUBLISHED.name)
            ),
            Update().inc("viewCount", 1),
            EducationProgram::class.java
        )
    }
}
