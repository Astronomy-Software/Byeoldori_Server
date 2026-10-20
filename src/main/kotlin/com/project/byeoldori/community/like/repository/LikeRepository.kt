package com.project.byeoldori.community.like.repository

import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.like.domain.LikeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeRepository : JpaRepository<LikeEntity, LikeId> {
    fun existsByPostIdAndUserId(postId: Long, userId: Long): Boolean
    fun countByPostId(postId: Long): Long

    // 동시 요청(더블클릭)이 겹쳐도 PK 중복 500 이 나지 않게 INSERT IGNORE 로 넣는다. 실제로 넣었으면 1, 이미 있으면 0
    @Modifying
    @Query(value = "INSERT IGNORE INTO likes (post_id, user_id) VALUES (:postId, :userId)", nativeQuery = true)
    fun insertIgnore(@Param("postId") postId: Long, @Param("userId") userId: Long): Int

    // 벌크 삭제 — 이미 지워진 행이면 0 을 돌려줄 뿐 예외가 나지 않는다
    @Modifying
    @Query("delete from LikeEntity l where l.post.id = :postId and l.user.id = :userId")
    fun deleteByPostIdAndUserId(@Param("postId") postId: Long, @Param("userId") userId: Long): Int

    @Query("SELECT l.post.id FROM LikeEntity l WHERE l.user.id = :userId AND l.post.id IN :postIds")
    fun findLikedPostIds(userId: Long, postIds: Collection<Long>): List<Long>
}