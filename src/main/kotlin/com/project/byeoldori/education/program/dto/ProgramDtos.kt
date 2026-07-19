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
    val subtitle: String? = null,
    val difficulty: EducationDifficulty? = null,
    val targets: List<String>? = null,
    val steps: List<Document>? = null
)

data class UpdateProgramRequest(
    @field:Size(max = 200)
    val title: String? = null,
    val subtitle: String? = null,
    val difficulty: EducationDifficulty? = null,
    val targets: List<String>? = null,
    val steps: List<Document>? = null
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
