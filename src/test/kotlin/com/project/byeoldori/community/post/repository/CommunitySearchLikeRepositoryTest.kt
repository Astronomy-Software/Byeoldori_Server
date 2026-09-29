package com.project.byeoldori.community.post.repository

import com.project.byeoldori.community.common.domain.EducationStatus
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.domain.EducationPost
import com.project.byeoldori.community.post.service.SearchKeyword
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/** 한 글자 검색어 LIKE 대체 쿼리 (H2 MySQL 모드, 실제 JPQL). FULLTEXT 는 MySQL 전용이라 운영 격리 DB 로 별도 검증. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommunitySearchLikeRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val postRepo: CommunityPostRepository,
) {
    private val page = PageRequest.of(0, 20)

    private fun setup(): User {
        val u = em.persist(User(email = "s@test.com", passwordHash = "x", name = "s", phone = "010", nickname = "검색러"))
        fun post(type: PostType, title: String, content: String) =
            em.persist(CommunityPost(author = u, type = type, title = title, content = content))
        post(PostType.FREE, "별 보러 가요", "달이 밝았다")
        post(PostType.FREE, "북두칠성 찾는 법", "100% 확실한 방법")
        post(PostType.FREE, "관측 후기", "구름이 많았다")
        val draft = post(PostType.EDUCATION, "별자리 기초(작성중)", "별")
        em.persist(EducationPost(post = draft, status = EducationStatus.DRAFT))
        val pub = post(PostType.EDUCATION, "별자리 입문", "별")
        em.persist(EducationPost(post = pub, status = EducationStatus.PUBLISHED))
        em.flush(); em.clear()
        return u
    }

    private fun like(raw: String) = (SearchKeyword.plan(raw) as SearchKeyword.Like).pattern

    @Test
    fun `한 글자 제목 검색`() {
        setup()
        assertThat(postRepo.findVisibleByTypeAndTitleLike(PostType.FREE, like("별"), page).content.map { it.title })
            .containsExactly("별 보러 가요")
    }

    @Test
    fun `한 글자 본문 검색`() {
        setup()
        assertThat(postRepo.findVisibleByTypeAndContentLike(PostType.FREE, like("달"), page).content.map { it.title })
            .containsExactly("별 보러 가요")
    }

    @Test
    fun `퍼센트는 와일드카드가 아니라 글자로 찾는다`() {
        setup()
        assertThat(postRepo.findVisibleByTypeAndContentLike(PostType.FREE, like("%"), page).content.map { it.title })
            .containsExactly("북두칠성 찾는 법")
    }

    @Test
    fun `교육 게시판 LIKE 검색도 작성 중 글은 숨긴다`() {
        setup()
        assertThat(postRepo.findVisibleByTypeAndTitleLike(PostType.EDUCATION, like("별"), page).content.map { it.title })
            .containsExactly("별자리 입문")
    }
}
