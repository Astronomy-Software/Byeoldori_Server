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

    // 내 응시 이력은 프로그램 제목만 쓴다 → steps 없이 제목(과 생성자 필수값 authorId)만 읽는다
    @Query(value = "{ '_id': { '\$in': ?0 } }", fields = "{ 'title': 1, 'authorId': 1 }")
    fun findTitlesByIdIn(ids: Collection<String>): List<EducationProgram>

    fun findByStatusAndTitleContaining(
        status: ProgramStatus,
        title: String,
        pageable: Pageable
    ): Page<EducationProgram>
}
