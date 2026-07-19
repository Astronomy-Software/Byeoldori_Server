package com.project.byeoldori.education.program.repository

import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface EducationProgramRepository : MongoRepository<EducationProgram, String> {

    fun findByStatus(status: ProgramStatus, pageable: Pageable): Page<EducationProgram>

    fun findByAuthorId(authorId: Long, pageable: Pageable): Page<EducationProgram>

    fun findByStatusAndTitleContaining(
        status: ProgramStatus,
        title: String,
        pageable: Pageable
    ): Page<EducationProgram>
}
