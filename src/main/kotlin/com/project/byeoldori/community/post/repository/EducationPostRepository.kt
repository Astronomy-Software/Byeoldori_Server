package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.post.domain.EducationPost
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
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

    // 같은 글에 대한 평가를 직렬화한다(동시 평가가 서로의 평점을 못 보고 계산하거나, 같은 사용자의 중복 INSERT 방지).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EducationPost e where e.id = :postId")
    fun findByIdForUpdate(@Param("postId") postId: Long): EducationPost?

    // 평점 수·평균을 education_rating 에서 한 번에 다시 계산한다.
    // 엔티티 필드를 ++ 하고 dirty-check 로 저장하면 동시 평가 시 한 건이 사라지므로 원자적 UPDATE 로 처리한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE education_post
               SET rating_count = (SELECT COUNT(*) FROM education_rating r WHERE r.education_post_id = :postId),
                   average_score = (SELECT COALESCE(ROUND(AVG(r.score), 1), 0) FROM education_rating r WHERE r.education_post_id = :postId)
             WHERE post_id = :postId
        """,
        nativeQuery = true
    )
    fun recalculateRating(@Param("postId") postId: Long): Int
}