package com.project.byeoldori.star.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "star",
    uniqueConstraints = [UniqueConstraint(
        name = "star_object_name",
        columnNames = ["object_name"]
    )]
)
class Star(
    @Id
    @Column(name = "object_name", nullable = false)
    val objectName: String,

    @Column(name = "review_count", nullable = false)
    var reviewCount: Int = 0,

    @Column(name = "education_count", nullable = false)
    var educationCount: Int = 0
)