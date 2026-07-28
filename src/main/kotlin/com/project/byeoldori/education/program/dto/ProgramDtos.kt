package com.project.byeoldori.education.program.dto

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.bson.Document
import java.time.LocalDateTime

data class CreateProgramRequest(
    @field:NotBlank(message = "제목은 필수입니다.")
    @field:Size(max = 200)
    val title: String,
    @field:Size(max = 300, message = "부제는 300자를 넘을 수 없습니다.")
    val subtitle: String? = null,
    val difficulty: EducationDifficulty? = null,
    @field:Size(max = 30, message = "대상 천체는 최대 30개입니다.")
    val targets: List<String>? = null,
    // 프론트 인터랙티브 씬 배열. 요청은 표준 Map 으로 받아(Jackson 안전) 서비스에서 org.bson.Document 로 변환.
    @field:Size(max = 500, message = "스텝은 최대 500개입니다.")
    val steps: List<Map<String, Any?>>? = null
)

data class UpdateProgramRequest(
    @field:Size(max = 200)
    val title: String? = null,
    @field:Size(max = 300, message = "부제는 300자를 넘을 수 없습니다.")
    val subtitle: String? = null,
    val difficulty: EducationDifficulty? = null,
    @field:Size(max = 30, message = "대상 천체는 최대 30개입니다.")
    val targets: List<String>? = null,
    @field:Size(max = 500, message = "스텝은 최대 500개입니다.")
    val steps: List<Map<String, Any?>>? = null
)

data class ProgramSummaryResponse(
    val id: String?,
    val title: String,
    val difficulty: EducationDifficulty?,
    val status: ProgramStatus,
    val authorName: String?,
    val viewCount: Long,
    val updatedAt: LocalDateTime?
) {
    companion object {
        fun from(p: EducationProgram) = ProgramSummaryResponse(
            id = p.id,
            title = p.title,
            difficulty = p.difficulty,
            status = p.status,
            authorName = p.authorName,
            viewCount = p.viewCount,
            updatedAt = p.updatedAt
        )
    }
}

data class ProgramDetailResponse(
    val id: String?,
    val title: String,
    val subtitle: String?,
    val difficulty: EducationDifficulty?,
    val schemaVersion: Int,
    val steps: List<Document>,
    val status: ProgramStatus,
    val authorId: Long
) {
    companion object {
        fun from(p: EducationProgram) = ProgramDetailResponse(
            id = p.id,
            title = p.title,
            subtitle = p.subtitle,
            difficulty = p.difficulty,
            schemaVersion = p.schemaVersion,
            steps = p.steps,
            status = p.status,
            authorId = p.authorId
        )
    }
}

data class IdStringResponse(val id: String?)
