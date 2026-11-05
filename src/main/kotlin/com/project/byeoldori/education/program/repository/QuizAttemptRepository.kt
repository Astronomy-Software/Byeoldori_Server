package com.project.byeoldori.education.program.repository

import com.project.byeoldori.education.program.domain.QuizAttempt
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository

interface QuizAttemptRepository : MongoRepository<QuizAttempt, String> {

    fun findByProgramId(programId: String, pageable: Pageable): Page<QuizAttempt>

    fun findByUserId(userId: Long, pageable: Pageable): Page<QuizAttempt>

    fun deleteByProgramId(programId: String)

    // 회원 탈퇴 시 응시 기록을 지운다
    fun deleteByUserId(userId: Long)
}
