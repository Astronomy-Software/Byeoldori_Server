package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.EducationStatus
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.domain.EducationPost
import com.project.byeoldori.community.post.domain.EducationRating
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/**
 * 교육 게시글 공개 범위·연결 정리·삭제 쿼리를 실제 JPA(H2 MySQL 모드)로 검증한다.
 * Docker 불필요 — application-test.properties 의 H2 를 그대로 쓴다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EducationVisibilityRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val postRepo: CommunityPostRepository,
    private val eduRepo: EducationPostRepository,
    private val ratingRepo: EducationRatingRepository,
) {
    private val page = PageRequest.of(0, 20)

    private fun user(n: Int) = em.persist(
        User(email = "u$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "별지기$n")
    )

    private fun post(author: User, type: PostType, title: String) =
        em.persist(CommunityPost(author = author, type = type, title = title, content = "본문"))

    private fun edu(author: User, title: String, status: EducationStatus, programId: String? = null): CommunityPost {
        val p = post(author, PostType.EDUCATION, title)
        em.persist(EducationPost(post = p, status = status, programId = programId))
        return p
    }

    @Test
    fun `공개 목록은 발행된 교육글만, 다른 게시판은 그대로`() {
        val u = user(1)
        edu(u, "발행됨", EducationStatus.PUBLISHED)
        edu(u, "작성중", EducationStatus.DRAFT)
        post(u, PostType.FREE, "자유글")
        em.flush(); em.clear()

        assertThat(postRepo.findVisibleByType(PostType.EDUCATION, page).content.map { it.title })
            .containsExactly("발행됨")
        assertThat(postRepo.findVisibleByType(PostType.EDUCATION, page).totalElements).isEqualTo(1)
        assertThat(postRepo.findVisibleByType(PostType.FREE, page).content.map { it.title })
            .containsExactly("자유글")
    }

    @Test
    fun `닉네임 검색도 작성 중 교육글을 거른다`() {
        val u = user(2)
        edu(u, "발행됨", EducationStatus.PUBLISHED)
        edu(u, "작성중", EducationStatus.DRAFT)
        em.flush(); em.clear()

        assertThat(postRepo.findVisibleByTypeAndAuthorNickname(PostType.EDUCATION, "지기2", page).content.map { it.title })
            .containsExactly("발행됨")
    }

    @Test
    fun `프로그램이 삭제되면 연결된 게시글의 programId 가 비워진다`() {
        val u = user(3)
        val a = edu(u, "A", EducationStatus.PUBLISHED, programId = "prog-1")
        val b = edu(u, "B", EducationStatus.PUBLISHED, programId = "prog-2")
        em.flush(); em.clear()

        assertThat(eduRepo.clearProgramId("prog-1")).isEqualTo(1)
        assertThat(eduRepo.findById(a.id!!).get().programId).isNull()
        assertThat(eduRepo.findById(b.id!!).get().programId).isEqualTo("prog-2")
    }

    @Test
    fun `평점을 먼저 지우면 평점 달린 교육글도 삭제된다`() {
        val author = user(4)
        val rater = user(5)
        val p = edu(author, "평점글", EducationStatus.PUBLISHED)
        em.persist(EducationRating(educationPost = eduRepo.findById(p.id!!).get(), user = rater, score = 5))
        em.flush(); em.clear()

        assertThat(ratingRepo.deleteAllByPostId(p.id!!)).isEqualTo(1)
        postRepo.deleteById(p.id!!)
        em.flush()
        assertThat(postRepo.findById(p.id!!)).isEmpty
    }
}
