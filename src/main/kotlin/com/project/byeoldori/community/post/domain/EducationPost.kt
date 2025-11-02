package com.project.byeoldori.community.post.domain

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.community.common.domain.EducationStatus
import com.project.byeoldori.star.entity.Star
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "star_object_name", referencedColumnName = "object_name")
    var star: Star? = null,

    @Column(name = "target_name")
    var targetName: String? = null,

    @Enumerated(EnumType.STRING)
    var difficulty: EducationDifficulty? = null,  // BEGINNER | INTERMEDIATE | ADVANCED

    var tags: String? = null,                     // "별자리,관측기초" 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EducationStatus = EducationStatus.DRAFT,

    @Column(name = "average_score", nullable = false)
    var averageScore: Double = 0.0, // 평균 평점

    @Column(name = "rating_count", nullable = false)
    var ratingCount: Long = 0, // 평점 참여자 수

    @OneToMany(mappedBy = "educationPost", cascade = [CascadeType.ALL], orphanRemoval = true)
    val ratings: MutableList<EducationRating> = mutableListOf()
)