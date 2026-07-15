package com.project.byeoldori.community.post.domain

import com.project.byeoldori.common.jpa.BaseTimeEntity
import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.user.entity.User
import jakarta.persistence.*

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

    // @Lob + length 미지정이면 Hibernate가 MySQL에서 TINYTEXT(255 bytes)로 생성해
    // 한글 약 85자만 넘어도 "Data too long"으로 저장이 실패한다. LONGTEXT를 명시한다.
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    var content: String,

    @Column(name = "view_count", nullable = false) var viewCount: Long = 0,
    @Column(name = "like_count", nullable = false) var likeCount: Long = 0,
    @Column(name = "comment_count", nullable = false) var commentCount: Long = 0,

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    val comments: MutableList<Comment> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    val likes: MutableList<LikeEntity> = mutableListOf(),

    @OneToMany(mappedBy = "post", cascade = [CascadeType.ALL], orphanRemoval = true)
    val images: MutableList<PostImage> = mutableListOf()
): BaseTimeEntity()