package com.project.byeoldori.star.entity

import jakarta.persistence.*

enum class ContentType { REVIEW, EDUCATION, EVENT }

@Entity
@Table(
    name = "content_target",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_ct_type_id_star",
            columnNames = ["content_type", "content_id", "star_object_name"]
        )
    ],
    indexes = [
        Index(name = "ix_ct_type_id", columnList = "content_type,content_id"),
        Index(name = "ix_ct_star", columnList = "star_object_name"),
        Index(name = "ix_ct_type_star", columnList = "content_type,star_object_name")
    ]
)
class ContentTarget(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    var contentType: ContentType,

    @Column(name = "content_id", nullable = false)
    var contentId: Long,

    @Column(name = "star_object_name", nullable = false)
    var starObjectName: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
)