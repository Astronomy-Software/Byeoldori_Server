package com.project.byeoldori.community.post.domain

import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDateTime

@IdClass(EducationRatingId::class)
@Entity
@Table(name = "education_rating")
class EducationRating(
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_post_id")
    val educationPost: EducationPost,

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User,

    @Column(nullable = false)
    var score: Int,

    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class EducationRatingId(
    val educationPost: Long = 0,
    val user: Long = 0
) : Serializable