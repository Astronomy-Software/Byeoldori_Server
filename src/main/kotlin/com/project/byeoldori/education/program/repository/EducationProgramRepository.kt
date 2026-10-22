package com.project.byeoldori.education.program.repository

import com.project.byeoldori.education.program.domain.EducationProgram
import com.project.byeoldori.education.program.domain.ProgramStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface EducationProgramRepository : MongoRepository<EducationProgram, String> {

    // 목록(요약)은 steps 를 쓰지 않는다. steps 는 프로그램당 최대 512KB 라, 문서 전체를 읽으면
    // 비로그인 공개 목록 한 페이지(최대 50개)에 수십 MB 를 Mongo 에서 읽어 와 버리게 된다 → steps 를 빼고 읽는다.
    @Query(value = "{ 'status': ?0 }", fields = "{ 'steps': 0 }")
    fun findSummariesByStatus(status: ProgramStatus, pageable: Pageable): Page<EducationProgram>

    @Query(value = "{ 'authorId': ?0 }", fields = "{ 'steps': 0 }")
    fun findSummariesByAuthorId(authorId: Long, pageable: Pageable): Page<EducationProgram>

    fun findByStatusAndTitleContaining(
        status: ProgramStatus,
        title: String,
        pageable: Pageable
    ): Page<EducationProgram>
}
