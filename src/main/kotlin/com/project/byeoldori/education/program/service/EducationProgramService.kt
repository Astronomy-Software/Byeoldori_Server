package com.project.byeoldori.education.program.service

import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.common.exception.ForbiddenException
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.common.exception.UnauthorizedException
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.common.dto.toPageResponse
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.dto.CreateProgramRequest
import com.project.byeoldori.education.program.dto.ProgramDetailResponse
import com.project.byeoldori.education.program.dto.ProgramSummaryResponse
import com.project.byeoldori.education.program.dto.UpdateProgramRequest
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.education.program.repository.QuizAttemptRepository
import com.project.byeoldori.community.post.repository.EducationPostRepository
import com.project.byeoldori.star.service.CONTENT_TARGET_MAX_COUNT
import com.project.byeoldori.star.service.CONTENT_TARGET_MAX_LENGTH
import com.project.byeoldori.star.service.normalizeTargetLabels
import com.project.byeoldori.user.entity.User
import com.project.byeoldori.user.repository.UserRepository
import com.project.byeoldori.user.utils.DeletedAccount
import org.bson.BsonInvalidOperationException
import org.bson.codecs.configuration.CodecConfigurationException
import org.springframework.data.domain.Page
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
    private val mongoTemplate: MongoTemplate,
    private val quizAttemptRepo: QuizAttemptRepository,
    private val educationPostRepo: EducationPostRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private const val MAX_STEPS_BYTES = 512 * 1024 // 512KB
    }

    private fun isAdmin(user: User): Boolean = user.roles.contains("ADMIN")

    private fun findOrThrow(id: String): EducationProgram =
        repo.findById(id).orElseThrow { NotFoundException(ErrorCode.EDU_PROGRAM_NOT_FOUND) }

    /**
     * 작성자 표시 이름은 응답 시점의 users 에서 가져온다.
     * 저장된 authorName 은 생성 시점 닉네임이라, 탈퇴하거나 닉네임을 바꿔도 공개 목록에 옛 닉네임이 계속 노출됐다.
     * 탈퇴(또는 없는) 계정은 게시글처럼 "탈퇴한 사용자"로 보여준다.
     * 단 authorId 가 0 이하인 기본 제공 프로그램(예: orion-101, 작성자 "별도리")은 사용자 계정이 아니므로 저장된 이름을 쓴다.
     */
    private fun displayName(p: EducationProgram, author: User?): String? = when {
        p.authorId <= 0 -> p.authorName
        author == null || author.deletedAt != null -> DeletedAccount.ANONYMOUS_NAME
        else -> author.nickname
    }

    private fun detail(p: EducationProgram): ProgramDetailResponse =
        ProgramDetailResponse.from(
            p,
            displayName(p, if (p.authorId <= 0) null else userRepository.findById(p.authorId).orElse(null)),
        )

    private fun summaries(page: Page<EducationProgram>): PageResponse<ProgramSummaryResponse> {
        val ids = page.content.map { it.authorId }.filter { it > 0 }.toSet()
        val authors = if (ids.isEmpty()) emptyMap() else userRepository.findAllById(ids).associateBy { it.id }
        return page.map { ProgramSummaryResponse.from(it, displayName(it, authors[it.authorId])) }.toPageResponse()
    }

    /**
     * steps 를 org.bson.Document 로 변환하며 전체 직렬화 크기를 제한한다.
     * @Size(max=500) 은 스텝 "개수"만 막으므로, 스텝 하나에 거대한 text/base64 이미지를
     * 넣는 식의 남용은 막지 못한다. 문서 하나가 Mongo 를 잠식하지 않도록 512KB 상한을 둔다.
     */
    private fun toSteps(raw: List<Map<String, Any?>>?): List<org.bson.Document> {
        if (raw.isNullOrEmpty()) return emptyList()
        val docs = raw.map { org.bson.Document(it) }
        // Long 범위를 넘는 정수(BigInteger) 등 BSON 으로 못 바꾸는 값은 코덱 예외(→500)라 400 으로 바꾼다
        val bytes = try {
            org.bson.Document("s", docs).toJson().toByteArray(Charsets.UTF_8).size
        } catch (e: CodecConfigurationException) {
            throw InvalidInputException("프로그램 형식이 올바르지 않습니다.")
        } catch (e: BsonInvalidOperationException) {
            throw InvalidInputException("프로그램 형식이 올바르지 않습니다.")
        }
        if (bytes > MAX_STEPS_BYTES) {
            throw InvalidInputException("프로그램 내용이 너무 큽니다(최대 512KB). 이미지 URL 은 링크로 넣어주세요.")
        }
        // 잘못된 quiz 스텝은 저장 시점에 막는다 — 채점 기준이 깨진 채 발행되지 않도록
        QuizSteps.validate(docs)
        return docs
    }

    /**
     * 대상 천체 라벨을 정리한다(앞뒤 공백 제거·빈 값/중복 제거) — 게시글 targets 와 같은 규칙.
     * @Size(max=30) 은 개수만 막아, 라벨 하나에 수 MB 문자열을 넣으면 512KB steps 상한을 우회해
     * 문서를 키울 수 있었다(목록·검수 큐는 steps 만 빼고 읽으므로 매번 통째로 읽힌다).
     */
    private fun normalizeTargets(raw: List<String>): List<String> {
        val uniq = normalizeTargetLabels(raw)
        if (uniq.size > CONTENT_TARGET_MAX_COUNT) {
            throw InvalidInputException("대상 천체는 최대 ${CONTENT_TARGET_MAX_COUNT}개입니다.")
        }
        if (uniq.any { it.length > CONTENT_TARGET_MAX_LENGTH }) {
            throw InvalidInputException("대상 천체 이름은 ${CONTENT_TARGET_MAX_LENGTH}자를 넘을 수 없습니다.")
        }
        return uniq
    }

    fun create(user: User, req: CreateProgramRequest): ProgramDetailResponse {
        val program = EducationProgram(
            title = req.title,
            subtitle = req.subtitle,
            difficulty = req.difficulty,
            targets = normalizeTargets(req.targets ?: emptyList()),
            authorId = user.id,
            authorName = user.nickname,
            status = ProgramStatus.DRAFT,
            steps = toSteps(req.steps)
        )
        val saved = repo.save(program)
        return ProgramDetailResponse.from(saved, displayName(saved, user))
    }

    fun update(id: String, req: UpdateProgramRequest, user: User): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.authorId != user.id) throw ForbiddenException()
        if (p.status != ProgramStatus.DRAFT && p.status != ProgramStatus.PREVIEW) {
            throw InvalidInputException("작성 중(DRAFT)이거나 검수 대기(PREVIEW) 상태에서만 수정할 수 있습니다.")
        }

        req.title?.let { p.title = it }
        req.subtitle?.let { p.subtitle = it }
        req.difficulty?.let { p.difficulty = it }
        req.targets?.let { p.targets = normalizeTargets(it) }
        req.steps?.let { p.steps = toSteps(it) }

        // 검수 대기 중 내용을 고치면 관리자가 본 버전과 발행될 버전이 달라진다.
        // 검수가 필요한 상황(작성자가 직접 발행할 수 없는 경우)이면 DRAFT 로 되돌려 다시 검수 요청을 받는다.
        if (p.status == ProgramStatus.PREVIEW && !isAdmin(user) && systemConfigService.moderationRequired()) {
            p.status = ProgramStatus.DRAFT
            p.rejectReason = null
        }

        return detail(repo.save(p))
    }

    /** DRAFT → PREVIEW (작성자 본인) */
    fun submit(id: String, user: User): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.authorId != user.id) throw ForbiddenException()
        if (p.status != ProgramStatus.DRAFT) {
            throw InvalidInputException("작성 중(DRAFT) 상태에서만 검수 요청을 할 수 있습니다.")
        }
        p.status = ProgramStatus.PREVIEW
        p.rejectReason = null
        return detail(repo.save(p))
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
        p.rejectReason = null
        return detail(repo.save(p))
    }

    /** PREVIEW → DRAFT (ADMIN 반려) */
    fun reject(id: String, user: User, reason: String? = null): ProgramDetailResponse {
        if (!isAdmin(user)) throw ForbiddenException("관리자만 반려할 수 있습니다.")
        val p = findOrThrow(id)
        if (p.status != ProgramStatus.PREVIEW) {
            throw InvalidInputException("검수 대기(PREVIEW) 상태에서만 반려할 수 있습니다.")
        }
        p.status = ProgramStatus.DRAFT
        p.rejectReason = reason?.trim()?.takeIf { it.isNotEmpty() }
        return detail(repo.save(p))
    }

    /** PUBLISHED 전체 공개 목록 */
    fun listPublished(pageable: Pageable): PageResponse<ProgramSummaryResponse> =
        summaries(repo.findSummariesByStatus(ProgramStatus.PUBLISHED, pageable))

    /** 작성자 본인의 전체 상태 목록 */
    fun listMine(user: User, pageable: Pageable): PageResponse<ProgramSummaryResponse> =
        summaries(repo.findSummariesByAuthorId(user.id, pageable))

    /** ADMIN 검수 큐(PREVIEW) 목록 */
    fun listPending(user: User, pageable: Pageable): PageResponse<ProgramSummaryResponse> {
        if (!isAdmin(user)) throw ForbiddenException("관리자만 검수 큐를 조회할 수 있습니다.")
        return summaries(repo.findSummariesByStatus(ProgramStatus.PREVIEW, pageable))
    }

    /** PUBLISHED 는 비로그인 포함 누구나. DRAFT/PREVIEW 는 작성자 또는 관리자만. */
    fun get(id: String, user: User?): ProgramDetailResponse {
        val p = findOrThrow(id)
        if (p.status != ProgramStatus.PUBLISHED) {
            // 익명(만료 토큰 포함)은 401 → 웹이 토큰 갱신 후 재시도. 로그인한 타인만 403.
            if (user == null) throw UnauthorizedException(ErrorCode.LOGIN_REQUIRED.message)
            val allowed = p.authorId == user.id || isAdmin(user)
            if (!allowed) throw ForbiddenException("해당 프로그램을 조회할 권한이 없습니다.")
        }
        return detail(p)
    }

    fun delete(id: String, user: User) {
        val p = findOrThrow(id)
        if (p.authorId != user.id && !isAdmin(user)) throw ForbiddenException()
        repo.delete(p)
        // 응시 기록은 프로그램 없이는 의미가 없다(통계·이력 제목도 못 붙인다)
        p.id?.let {
            quizAttemptRepo.deleteByProgramId(it)
            // 이 프로그램을 연결한 교육 게시글의 "프로그램 실행" 링크가 깨지지 않도록 연결을 끊는다
            educationPostRepo.clearProgramId(it)
        }
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
