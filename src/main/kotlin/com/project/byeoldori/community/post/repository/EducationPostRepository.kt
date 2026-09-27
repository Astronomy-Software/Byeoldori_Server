package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.EducationPost
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface EducationPostRepository : JpaRepository<EducationPost, Long> {

    // 교육 프로그램(Mongo)이 삭제되면 그 id 를 가리키던 게시글 연결을 끊는다(깨진 "프로그램 실행" 링크 방지).
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EducationPost e set e.programId = null where e.programId = :programId")
    fun clearProgramId(@Param("programId") programId: String): Int
}