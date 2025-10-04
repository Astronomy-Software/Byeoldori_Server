package com.project.byeoldori.community.post.domain

import com.project.byeoldori.observationsites.entity.ObservationSite
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.LocalDate

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

    // [추가] 관측지와의 연결 (Many-to-One)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "observation_site_id")
    var observationSite: ObservationSite? = null,

    var location: String? = null,
    var target: String? = null,
    var equipment: String? = null,

    @Column(name = "observation_dt")
    var observationDate: LocalDate? = null,

    var score: Int? = null // 1~5점
)