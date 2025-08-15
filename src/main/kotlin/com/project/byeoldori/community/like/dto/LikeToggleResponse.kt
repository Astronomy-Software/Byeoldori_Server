package com.project.byeoldori.community.like.dto

data class LikeToggleResponse(
    val liked: Boolean, // 현재 사용자의 최종 상태 (true면 ON)
    val likes: Long     // 총 좋아요 수
)