package com.project.byeoldori.community.post.domain

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.community.common.domain.EducationStatus
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

@Entity
@Table(name = "education_post")
class EducationPost(
    @Id
    val id: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: CommunityPost,

    var summary: String? = null,

    @Enumerated(EnumType.STRING)
    var difficulty: EducationDifficulty? = null,  // BEGINNER | INTERMEDIATE | ADVANCED

    var tags: String? = null,                     // "별자리,관측기초" 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EducationStatus = EducationStatus.DRAFT
)