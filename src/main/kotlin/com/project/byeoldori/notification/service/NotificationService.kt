package com.project.byeoldori.notification.service

interface NotificationService {
    fun notifyNewComment(postAuthorId: Long, commenterName: String, preview: String)
    fun notifyCommentLiked(commentAuthorId: Long, likerName: String)
    fun sendToUser(userId: Long, title: String, body: String)
}
