package com.project.byeoldori.community.comment.service

import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 같은 댓글 DELETE 가 겹쳐도 comment_count 는 한 번만 줄어야 한다.
 * 잠금 없이 읽으면 두 트랜잭션이 모두 deleted=false 를 보고 두 번 감소시켰다 → 댓글 행을 FOR UPDATE 로 읽는다.
 */
class CommentDeleteLockTest {

    private val postRepo = mock(CommunityPostRepository::class.java)
    private val commentRepo = mock(CommentRepository::class.java)
    private val service = CommentService(
        postRepo, commentRepo, mock(CommentLikeRepository::class.java), mock(NotificationService::class.java)
    )

    private val author = User(id = 1, email = "a@test.com", passwordHash = "x", name = "a", phone = "010", nickname = "작성자")
    private val post = CommunityPost(id = 10, author = author, type = PostType.FREE, title = "글", content = "본문")

    @Test
    fun `삭제는 댓글 행을 잠금 조회로 읽고 잠금 없는 조회는 쓰지 않는다`() {
        val c = Comment(id = 100, post = post, author = author, content = "댓글")
        `when`(commentRepo.findByIdForUpdate(100)).thenReturn(c)

        service.delete(10, 100, author)

        assertThat(c.deleted).isTrue()
        verify(commentRepo).findByIdForUpdate(100)
        verify(commentRepo, never()).findById(anyLong())
        verify(postRepo, times(1)).addCommentCount(10, -1)
    }

    @Test
    fun `잠금을 기다린 뒤 이미 지워진 댓글을 보면 카운터를 줄이지 않는다`() {
        // 앞선 요청이 커밋한 뒤의 최신 행(deleted=true)을 잠금 조회가 돌려준 상황
        val c = Comment(id = 101, post = post, author = author, content = "삭제된 댓글입니다.", deleted = true)
        `when`(commentRepo.findByIdForUpdate(101)).thenReturn(c)

        service.delete(10, 101, author)

        verify(postRepo, never()).addCommentCount(anyLong(), anyLong())
    }

    @Test
    fun `없는 댓글 삭제는 404`() {
        `when`(commentRepo.findByIdForUpdate(999)).thenReturn(null)

        assertThatThrownBy { service.delete(10, 999, author) }.isInstanceOf(NotFoundException::class.java)
        verify(postRepo, never()).addCommentCount(anyLong(), anyLong())
    }
}
