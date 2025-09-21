package com.project.byeoldori.common.jpa

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.LocalDateTime

@MappedSuperclass
abstract class BaseTimeEntity {
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

    @PrePersist fun onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt }
    @PreUpdate  fun onUpdate() { updatedAt = LocalDateTime.now() }
}