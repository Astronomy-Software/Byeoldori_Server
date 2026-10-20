package com.project.byeoldori.community.like.service

import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.comment.service.CommentService
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.like.repository.CommentLikeRepository
import com.project.byeoldori.community.like.repository.LikeRepository
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.notification.service.NotificationService
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * 커뮤니티 카운터(댓글 수·좋아요 수)와 좋아요 토글을 실제 JPA(H2 MySQL 모드)로 검증한다.
 * - 카운터는 원자적 증감이라 영속성 컨텍스트의 낡은 값으로 다른 카운터를 덮어쓰지 않는다.
 * - 같은 좋아요가 겹쳐 들어와도 PK 중복 예외 없이 무시된다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityCounterTest @Autowired constructor(
    private val em: TestEntityManager,
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository,
    private val likeRepo: LikeRepository,
    private val commentLikeRepo: CommentLikeRepository,
) {
    private val notificationService = mock(NotificationService::class.java)
    private val likeService = LikeService(likeRepo, commentRepo, postRepo, commentLikeRepo, notificationService)
    private val commentService = CommentService(postRepo, commentRepo, commentLikeRepo, notificationService)

    private fun user(n: Int) = em.persist(
        User(email = "c$n@test.com", passwordHash = "x", name = "c$n", phone = "010", nickname = "카운터$n")
    )

    private fun post(author: User) =
        em.persist(CommunityPost(author = author, type = PostType.FREE, title = "자유글", content = "본문"))

    /** 다른 트랜잭션이 커밋한 것처럼 DB 값만 바꾸고, 영속성 컨텍스트의 엔티티는 낡은 값으로 둔다. */
    private fun bumpInDbOnly(sql: String, id: Long) {
        em.entityManager.createNativeQuery(sql).setParameter(1, id).executeUpdate()
    }

    private fun reload(id: Long): CommunityPost {
        em.flush(); em.clear()
        return postRepo.findById(id).get()
    }

    @Test
    fun `댓글 작성·삭제는 그 사이 올라간 조회수와 좋아요 수를 덮어쓰지 않는다`() {
        val author = user(1)
        val writer = user(2)
        val p = post(author)
        em.flush()

        bumpInDbOnly("update community set view_count = 5, like_count = 3 where id = ?1", p.id!!)
        val c = commentService.write(p.id!!, writer, "댓글")

        val afterWrite = reload(p.id!!)
        assertThat(afterWrite.commentCount).isEqualTo(1)
        assertThat(afterWrite.viewCount).isEqualTo(5)
        assertThat(afterWrite.likeCount).isEqualTo(3)

        bumpInDbOnly("update community set view_count = 9 where id = ?1", p.id!!)
        commentService.delete(p.id!!, c.id, writer)
        commentService.delete(p.id!!, c.id, writer) // 두 번 지워도 한 번만 줄어든다

        val afterDelete = reload(p.id!!)
        assertThat(afterDelete.commentCount).isZero()
        assertThat(afterDelete.viewCount).isEqualTo(9)
        assertThat(afterDelete.likeCount).isEqualTo(3)
    }

    @Test
    fun `글을 수정해도 낡은 카운터로 덮어쓰지 않는다`() {
        val author = user(3)
        val p = post(author)
        em.flush()

        bumpInDbOnly("update community set like_count = 7, comment_count = 2 where id = ?1", p.id!!)
        p.title = "수정된 제목"

        val after = reload(p.id!!)
        assertThat(after.title).isEqualTo("수정된 제목")
        assertThat(after.likeCount).isEqualTo(7)
        assertThat(after.commentCount).isEqualTo(2)
    }

    @Test
    fun `게시글 좋아요 토글은 카운터를 원자적으로 움직이고 다른 카운터는 건드리지 않는다`() {
        val author = user(4)
        val liker = user(5)
        val p = post(author)
        em.flush()
        bumpInDbOnly("update community set view_count = 11 where id = ?1", p.id!!)

        val on = likeService.toggleAndCount(p.id!!, liker)
        assertThat(on.liked).isTrue()
        assertThat(on.likes).isEqualTo(1)
        assertThat(reload(p.id!!).likeCount).isEqualTo(1)

        val off = likeService.toggleAndCount(p.id!!, liker)
        assertThat(off.liked).isFalse()
        assertThat(off.likes).isZero()
        val after = reload(p.id!!)
        assertThat(after.likeCount).isZero()
        assertThat(after.viewCount).isEqualTo(11)
        assertThat(likeRepo.countByPostId(p.id!!)).isZero()
    }

    @Test
    fun `겹친 좋아요 요청은 PK 중복 예외 없이 무시된다`() {
        val author = user(6)
        val liker = user(7)
        val p = post(author)
        val c = em.persist(Comment(post = p, author = author, content = "댓글"))
        em.flush()

        // 먼저 확인한 요청이 넣기 전에 다른 요청이 같은 행을 넣은 상황
        assertThat(likeRepo.insertIgnore(p.id!!, liker.id)).isEqualTo(1)
        assertThat(likeRepo.insertIgnore(p.id!!, liker.id)).isZero()
        assertThat(commentLikeRepo.insertIgnore(c.id!!, liker.id, LocalDateTime.now())).isEqualTo(1)
        assertThat(commentLikeRepo.insertIgnore(c.id!!, liker.id, LocalDateTime.now())).isZero()

        // 이미 지워진 행을 다시 지워도 예외 없이 0
        assertThat(likeRepo.deleteByPostIdAndUserId(p.id!!, liker.id)).isEqualTo(1)
        assertThat(likeRepo.deleteByPostIdAndUserId(p.id!!, liker.id)).isZero()
        assertThat(commentLikeRepo.deleteByCommentIdAndUserId(c.id!!, liker.id)).isEqualTo(1)
        assertThat(commentLikeRepo.deleteByCommentIdAndUserId(c.id!!, liker.id)).isZero()
    }

    @Test
    fun `댓글 좋아요 토글은 카운터를 맞추고 알림은 실제로 추가됐을 때만 보낸다`() {
        val author = user(8)
        val liker = user(9)
        val p = post(author)
        val c = em.persist(Comment(post = p, author = author, content = "댓글"))
        em.flush()

        val on = likeService.toggleCommentLike(p.id!!, c.id!!, liker)
        assertThat(on.liked).isTrue()
        assertThat(on.likes).isEqualTo(1)

        val off = likeService.toggleCommentLike(p.id!!, c.id!!, liker)
        assertThat(off.liked).isFalse()
        assertThat(off.likes).isZero()

        em.flush(); em.clear()
        assertThat(commentRepo.findById(c.id!!).get().likeCount).isZero()
        verify(notificationService, times(1)).notifyCommentLiked(author.id, "카운터9")
    }
}
