package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.comment.domain.Comment
import com.project.byeoldori.community.comment.repository.CommentRepository
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.like.domain.CommentLike
import com.project.byeoldori.community.like.domain.LikeEntity
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

/**
 * 게시글 삭제가 댓글·대댓글·좋아요가 달린 상태에서도 FK 에 걸리지 않는지 실제 JPA(H2 MySQL 모드)로 검증한다.
 * 댓글 삭제는 soft delete 라 대댓글 행이 항상 남으므로, 부모 댓글이 먼저 지워지면 parent_id FK 에 걸릴 수 있다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunityPostDeleteRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val postRepo: CommunityPostRepository,
    private val commentRepo: CommentRepository,
) {
    private fun user(n: Int) = em.persist(
        User(email = "d$n@test.com", passwordHash = "x", name = "d$n", phone = "010", nickname = "삭제$n")
    )

    private fun post(author: User) =
        em.persist(CommunityPost(author = author, type = PostType.FREE, title = "자유글", content = "본문"))

    @Test
    fun `댓글만 있는 글은 삭제된다`() {
        val u = user(1)
        val p = post(u)
        em.persist(Comment(post = p, author = u, content = "댓글"))
        em.flush(); em.clear()

        postRepo.delete(postRepo.findById(p.id!!).get())
        em.flush()

        assertThat(postRepo.findById(p.id!!)).isEmpty
        assertThat(commentRepo.count()).isZero()
    }

    @Test
    fun `대댓글과 좋아요가 달린 글도 삭제된다`() {
        val author = user(2)
        val other = user(3)
        val p = post(author)
        val parent = em.persist(Comment(post = p, author = other, content = "부모 댓글"))
        val reply = em.persist(Comment(post = p, author = author, content = "대댓글", parent = parent, depth = 1))
        em.persist(CommentLike(comment = reply, user = other))
        em.persist(CommentLike(comment = parent, user = author))
        em.persist(LikeEntity(post = p, user = other))
        em.flush(); em.clear()

        postRepo.delete(postRepo.findById(p.id!!).get())
        em.flush()

        assertThat(postRepo.findById(p.id!!)).isEmpty
        assertThat(commentRepo.count()).isZero()
    }
}
