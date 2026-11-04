package com.project.byeoldori.community.post.domain

import com.project.byeoldori.community.common.domain.EducationDifficulty
import com.project.byeoldori.community.common.domain.EducationStatus
import jakarta.persistence.*
import org.hibernate.annotations.DynamicUpdate
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

// 바뀐 컬럼만 UPDATE 한다 — 평점 재계산·프로그램 연결 해제(clearProgramId)와 글 수정이 겹쳐도
// 서로의 컬럼을 트랜잭션 시작 때 읽은 옛 값으로 덮어쓰지 않도록.
@Entity
@DynamicUpdate
@Table(name = "education_post")
class EducationPost(
    @Id
    val id: Long? = null,

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    var post: CommunityPost,
    
    @Enumerated(EnumType.STRING)
    var difficulty: EducationDifficulty? = null,  // BEGINNER | INTERMEDIATE | ADVANCED

    var tags: String? = null,                     // "별자리,관측기초" 등

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EducationStatus = EducationStatus.DRAFT,

    @Column(name = "content_url", length = 1024)
    var contentUrl: String? = null,

    // 연결된 인터랙티브 교육 프로그램(MongoDB education_programs)의 id.
    // 게시글은 MySQL, 프로그램(씬 JSON)은 MongoDB 로 분리돼 있어 id 로만 잇는다.
    // null 이면 재생할 프로그램이 없는 일반 교육 글.
    @Column(name = "program_id", length = 64)
    var programId: String? = null,

    @Column(name = "average_score", nullable = false)
    var averageScore: Double = 0.0, // 평균 평점

    @Column(name = "rating_count", nullable = false)
    var ratingCount: Long = 0, // 평점 참여자 수

    @OneToMany(mappedBy = "educationPost", cascade = [CascadeType.ALL], orphanRemoval = true)
    val ratings: MutableList<EducationRating> = mutableListOf()
)