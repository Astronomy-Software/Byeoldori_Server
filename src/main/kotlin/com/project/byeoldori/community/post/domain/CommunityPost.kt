package com.project.byeoldori.community.post.domain

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "community")
class CommunityPost(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    var author: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var type: PostType, // REVIEW | FREE | EDUCATION

    @Column(nullable = false, length = 120)
    var title: String,

    @Lob
    @Column(nullable = false)
    var content: String,

    @Column(name = "view_count", nullable = false) var viewCount: Long = 0,
    @Column(name = "like_count", nullable = false) var likeCount: Long = 0,
    @Column(name = "comment_count", nullable = false) var commentCount: Long = 0,

    // DB 기본값/ON UPDATE 사용 시 insertable/updatable=false로 두면 됨
    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null
)