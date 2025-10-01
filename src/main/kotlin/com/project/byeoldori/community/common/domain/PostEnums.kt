package com.project.byeoldori.community.common.domain

enum class PostType { REVIEW, FREE, EDUCATION }

enum class EducationDifficulty { BEGINNER, INTERMEDIATE, ADVANCED }

enum class EducationStatus { DRAFT, PUBLISHED }

enum class PostSortBy(val property: String) {
    LATEST("createdAt"),
    VIEWS("viewCount"),
    LIKES("likeCount")
}

enum class PostSearchBy {
    TITLE,
    CONTENT,
    NICKNAME
}