package com.project.byeoldori.community.post.domain

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "review_post")
class ReviewPost(
    @Id
    val id: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: CommunityPost,

    var location: String? = null,
    var target: String? = null,
    var equipment: String? = null,

    @Column(name = "observation_dt")
    var observationDate: LocalDateTime? = null,

    var score: Int? = null // 1~5점
)