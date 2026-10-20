package com.project.byeoldori.common.paging

import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.comment.service.CommentService
import com.project.byeoldori.community.common.domain.PostSearchBy
import com.project.byeoldori.community.common.domain.PostSortBy
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.common.dto.PageResponse
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.like.service.LikeService
import com.project.byeoldori.community.post.controller.PostController
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.community.post.service.PostService
import com.project.byeoldori.notification.controller.NotificationController
import com.project.byeoldori.notification.repository.NotificationRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

/**
 * 게시글·댓글·알림 목록의 page/size 보정.
 * 예전에는 page=-1·size=0 이 IllegalArgumentException(→500)이었고, size 상한이 없어 ?size=100000 이 그대로 통과했다.
 * Mockito 기본 응답(Answer)으로 넘어간 Pageable 만 붙잡는다(Kotlin 에서 matcher 없이 쓰기 위함).
 */
class ListPagingBoundsTest {

    private val user = User(id = 1, email = "p@test.com", passwordHash = "x", name = "p", phone = "010")

    private class Captured { var pageable: Pageable? = null }

    // 호출 인자 중 Pageable 을 붙잡고, 반환 타입에 맞는 빈 페이지를 돌려주는 mock
    private inline fun <reified T> capturingMock(captured: Captured): T = mock(T::class.java) { inv ->
        inv.arguments.filterIsInstance<Pageable>().firstOrNull()?.let { captured.pageable = it }
        when (inv.method.returnType) {
            PageResponse::class.java -> PageResponse<Any>(emptyList(), 0, 0, 0, 0)
            org.springframework.data.domain.Page::class.java ->
                PageImpl<Any>(emptyList(), captured.pageable ?: Pageable.unpaged(), 0)
            else -> null
        }
    }

    @Test
    fun `게시글 목록 - 음수 page·0 size 는 보정, 큰 size 는 50 으로 제한`() {
        val captured = Captured()
        val controller = PostController(capturingMock<PostService>(captured), mock(LikeService::class.java))

        controller.list(PostType.FREE, -1, 0, PostSortBy.LATEST, null, PostSearchBy.TITLE, user)
        assertThat(captured.pageable!!.pageNumber).isEqualTo(0)
        assertThat(captured.pageable!!.pageSize).isEqualTo(1)

        controller.list(PostType.FREE, 2, 100000, PostSortBy.LATEST, null, PostSearchBy.TITLE, user)
        assertThat(captured.pageable!!.pageNumber).isEqualTo(2)
        assertThat(captured.pageable!!.pageSize).isEqualTo(50)
    }

    @Test
    fun `댓글 목록 - 범위 밖 값은 보정되고 응답에도 보정값이 실린다`() {
        val captured = Captured()
        val service = CommentService(
            mock(CommunityPostRepository::class.java),
            capturingMock<CommentRepository>(captured),
            mock(CommentLikeRepository::class.java),
            mock(NotificationService::class.java),
        )

        val res = service.list(postId = 1, page = -3, size = 100000, user = null)
        assertThat(captured.pageable!!.pageNumber).isEqualTo(0)
        assertThat(captured.pageable!!.pageSize).isEqualTo(100)
        assertThat(res.page).isEqualTo(0)
        assertThat(res.size).isEqualTo(100)

        service.list(postId = 1, page = 0, size = -5, user = null)
        assertThat(captured.pageable!!.pageSize).isEqualTo(1)
    }

    @Test
    fun `알림 목록 - 음수 page 는 500 대신 0, size 는 50 으로 제한`() {
        val captured = Captured()
        val controller = NotificationController(capturingMock<NotificationRepository>(captured))

        controller.list(user, -1, 100000)
        assertThat(captured.pageable!!.pageNumber).isEqualTo(0)
        assertThat(captured.pageable!!.pageSize).isEqualTo(50)
    }
}
