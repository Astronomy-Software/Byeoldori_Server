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
import org.springframework.test.context.ActiveProfiles

/**
 * 교육글 평점 수·평균은 education_rating 에서 원자적 UPDATE 로 다시 계산한다.
 * 예전에는 ratingCount++ 와 평균 대입을 dirty-check 로 저장해, 동시 평가 시 한 건이 사라지고
 * 행 전체(program_id 등)를 옛 값으로 다시 썼다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EducationRatingRecalcRepositoryTest @Autowired constructor(
    private val em: TestEntityManager,
    private val eduRepo: EducationPostRepository,
) {
    private fun user(n: Int) = em.persist(
        User(email = "r$n@test.com", passwordHash = "x", name = "r$n", phone = "010", nickname = "평가자$n")
    )

    @Test
    fun `평점 수와 평균을 평점 테이블 기준으로 다시 계산하고 다른 컬럼은 건드리지 않는다`() {
        val author = user(0)
        val post = em.persist(CommunityPost(author = author, type = PostType.EDUCATION, title = "교육글", content = "본문"))
        // 이미 틀어진 값(rating_count=7, 평균 1.0)으로 시작
        val edu = em.persist(
            EducationPost(post = post, status = EducationStatus.PUBLISHED, programId = "prog-1", averageScore = 1.0, ratingCount = 7)
        )
        em.persist(EducationRating(educationPost = edu, user = user(1), score = 4))
        em.persist(EducationRating(educationPost = edu, user = user(2), score = 5))
        em.persist(EducationRating(educationPost = edu, user = user(3), score = 5))
        val id = post.id!!

        eduRepo.recalculateRating(id)

        val reloaded = eduRepo.findByIdForUpdate(id)!!
        assertThat(reloaded.ratingCount).isEqualTo(3)
        assertThat(reloaded.averageScore).isEqualTo(4.7)
        assertThat(reloaded.programId).isEqualTo("prog-1")
        assertThat(reloaded.status).isEqualTo(EducationStatus.PUBLISHED)
    }

    @Test
    fun `평점이 하나도 없으면 0건 평균 0 으로 맞춘다`() {
        val post = em.persist(CommunityPost(author = user(9), type = PostType.EDUCATION, title = "빈 교육글", content = "본문"))
        em.persist(EducationPost(post = post, status = EducationStatus.PUBLISHED, averageScore = 3.0, ratingCount = 2))
        val id = post.id!!

        eduRepo.recalculateRating(id)

        val reloaded = eduRepo.findById(id).get()
        assertThat(reloaded.ratingCount).isZero()
        assertThat(reloaded.averageScore).isEqualTo(0.0)
    }
}
