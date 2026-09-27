package com.project.byeoldori.education.program.migration

import com.fasterxml.jackson.databind.ObjectMapper
import com.project.byeoldori.admin.service.SystemConfigService
import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.community.common.domain.EducationStatus
import com.project.byeoldori.community.post.repository.EducationPostRepository
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import com.project.byeoldori.education.program.repository.EducationProgramRepository
import com.project.byeoldori.star.entity.ContentType
import com.project.byeoldori.star.repository.ContentTargetRepository
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 서버 기동 시 1회만 도는 데이터 이관. SystemConfig 에 완료 표시를 남겨 두 번째부터는 건너뛴다.
 *
 * 1) 교육 게시글의 예전 JSON 프로그램(education_post.content_url) → MongoDB education_programs.
 *    게시글은 program_id 로 새 프로그램을 가리키고 content_url 은 비운다. JSON 파일은 백업으로 남긴다.
 *    파일을 찾지 못하거나 해석하지 못한 글은 건드리지 않고 로그만 남긴다(GCP 시절 파일은 이미 없다).
 * 2) 저장된 URL 의 예전 호스트(byeoldori.duckdns.org) → 현재 공개 주소. DuckDNS 도메인 은퇴 준비.
 *
 * 테스트(H2, Mongo 없음)에서는 byeoldori.migration.enabled=false 로 끈다.
 */
@Component
@Order(100)
@ConditionalOnProperty(name = ["byeoldori.migration.enabled"], havingValue = "true", matchIfMissing = true)
class LegacyEducationMigration(
    private val eduRepo: EducationPostRepository,
    private val programRepo: EducationProgramRepository,
    private val targetRepo: ContentTargetRepository,
    private val systemConfig: SystemConfigService,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${storage.local.base-dir}") private val baseDir: String,
    @Value("\${storage.public-base-url}") private val publicBaseUrl: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val JSON_TO_MONGO_KEY = "migration.edu-json-to-mongo.v1"
        const val DUCKDNS_KEY = "migration.duckdns-url-rewrite.v1"
        const val OLD_HOST = "https://byeoldori.duckdns.org"
    }

    override fun run(args: ApplicationArguments) {
        runOnce(JSON_TO_MONGO_KEY) { migrateJsonPrograms() }
        runOnce(DUCKDNS_KEY) { rewriteDuckDnsUrls() }
    }

    private fun runOnce(key: String, block: () -> String) {
        if (systemConfig.getBool(key, false)) return
        try {
            val summary = block()
            systemConfig.set(key, "true")
            log.info("[migration] {} 완료: {}", key, summary)
        } catch (e: Exception) {
            // 실패하면 완료 표시를 남기지 않아 다음 기동 때 다시 시도한다. 앱 기동은 막지 않는다.
            log.error("[migration] {} 실패 — 다음 기동 때 재시도", key, e)
        }
    }

    // ── 1) JSON → Mongo ─────────────────────────────────────────
    private fun migrateJsonPrograms(): String {
        val candidates = eduRepo.findAll().filter { !it.contentUrl.isNullOrBlank() && it.programId == null }
        var migrated = 0
        val skipped = mutableListOf<String>()

        for (ep in candidates) {
            val postId = ep.id ?: continue
            val url = ep.contentUrl!!
            val json = readLegacyJson(url)
            if (json == null) {
                skipped += "post=$postId(파일 없음)"
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val rawSteps = json["steps"] as? List<Map<String, Any?>>
            if (rawSteps.isNullOrEmpty()) {
                skipped += "post=$postId(steps 없음)"
                continue
            }

            tx.executeWithoutResult {
                val post = eduRepo.findById(postId).orElseThrow()
                val author = post.post.author
                val targets = targetRepo.findAllByContentTypeAndContentId(ContentType.EDUCATION, postId)
                    .sortedBy { it.sortOrder }.map { it.starObjectName }
                val program = programRepo.save(
                    EducationProgram(
                        title = (json["title"] as? String)?.takeIf { it.isNotBlank() } ?: post.post.title,
                        subtitle = json["subtitle"] as? String,
                        difficulty = post.difficulty ?: parseDifficulty(json["difficulty"]),
                        targets = targets,
                        authorId = author.id,
                        authorName = author.nickname,
                        status = if (post.status == EducationStatus.PUBLISHED) ProgramStatus.PUBLISHED else ProgramStatus.DRAFT,
                        steps = rawSteps.map { Document(it) }
                    )
                )
                post.programId = program.id
                post.contentUrl = null
                eduRepo.save(post)
                log.info("[migration] 교육 게시글 {} → 프로그램 {} (JSON 백업 유지: {})", postId, program.id, url)
            }
            migrated++
        }
        return "대상 ${candidates.size}건, 이관 ${migrated}건, 건너뜀 ${skipped.size}건 ${skipped}"
    }

    /** 예전 JSON URL 을 로컬 디스크 경로로 바꿔 읽는다. 호스트는 무시하고 경로만 쓴다. */
    private fun readLegacyJson(url: String): Map<String, Any?>? {
        val relative = legacyJsonRelativePath(url) ?: return null
        val root = Paths.get(baseDir).toAbsolutePath().normalize()
        val file: Path = root.resolve(relative).normalize()
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null
        return try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(file.toFile(), Map::class.java) as Map<String, Any?>
        } catch (e: Exception) {
            log.warn("[migration] JSON 해석 실패: {} ({})", file, e.message)
            null
        }
    }

    private fun parseDifficulty(v: Any?): EducationDifficulty? =
        (v as? String)?.let { s -> EducationDifficulty.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } }

    // ── 2) DuckDNS URL 치환 ─────────────────────────────────────
    private fun rewriteDuckDnsUrls(): String {
        val newHost = publicBaseUrl.trimEnd('/')
        // 운영 공개 주소가 아직 DuckDNS 거나 로컬이면 치환할 이유가 없다
        if (newHost == OLD_HOST || !newHost.startsWith("https://")) return "건너뜀(publicBaseUrl=$newHost)"

        val like = "%byeoldori.duckdns.org%"
        val targets = listOf(
            "community" to "content",
            "post_image" to "url",
            "users" to "profile_image_url",
            "calendar_image" to "url",
            "education_post" to "content_url",
        )
        val counts = tx.execute {
            targets.associate { (table, col) ->
                "$table.$col" to jdbc.update(
                    "UPDATE $table SET $col = REPLACE($col, ?, ?) WHERE $col LIKE ?",
                    OLD_HOST, newHost, like
                )
            }
        } ?: emptyMap()

        // Mongo 교육 프로그램 steps 안의 이미지 URL
        var programs = 0
        programRepo.findAll().forEach { p ->
            val json = Document("s", p.steps).toJson()
            if (json.contains("byeoldori.duckdns.org")) {
                @Suppress("UNCHECKED_CAST")
                p.steps = (Document.parse(json.replace(OLD_HOST, newHost))["s"] as List<Document>)
                programRepo.save(p)
                programs++
            }
        }
        return "MySQL $counts, Mongo programs=$programs"
    }
}

/**
 * 예전 JSON URL → 업로드 폴더 기준 상대경로. 호스트는 무시한다(duckdns·GCP·api 무엇이든 경로만 본다).
 * 두 형식이 섞여 있다: .../files/json/2026/07/10/x.json(서빙 경로) 또는 .../json/2026/07/10/x.json.
 * .json 이 아니거나 형식이 다르면 null.
 */
internal fun legacyJsonRelativePath(url: String): String? {
    val path = try { URI(url.trim()).path } catch (e: Exception) { null } ?: return null
    if (!path.lowercase().endsWith(".json")) return null
    return when {
        path.contains("/files/") -> path.substringAfter("/files/")
        path.startsWith("/json/") -> path.removePrefix("/")
        else -> null
    }
}
