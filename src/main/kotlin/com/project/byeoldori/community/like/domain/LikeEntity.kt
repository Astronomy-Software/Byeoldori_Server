package com.project.byeoldori.community.like.domain

import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@IdClass(LikeId::class)
@Entity
@Table(name = "likes")
class LikeEntity(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val post: CommunityPost,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null
)

data class LikeId(
    val post: Long = 0,
    val user: Long = 0
) : Serializable