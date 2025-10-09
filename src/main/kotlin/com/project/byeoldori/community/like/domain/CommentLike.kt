package com.project.byeoldori.community.like.domain

import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@IdClass(CommentLikeId::class)
@Entity
@Table(name = "comment_likes")
class CommentLike(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val comment: Comment,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class CommentLikeId(
    val comment: Long = 0,
    val user: Long = 0
) : Serializable