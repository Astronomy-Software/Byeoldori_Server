package com.project.byeoldori.observationsites

import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.domain.ReviewPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.ActiveProfiles
import java.io.File

/**
 * 즐겨찾기 중복 정리·UNIQUE(V9)와 관측지별 후기 조회를 실제 JPA(H2 MySQL 모드)로 검증한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserSavedSiteUniqueTest @Autowired constructor(
    private val em: TestEntityManager,
    private val postRepo: CommunityPostRepository,
) {
    private fun user(n: Int) = em.persist(User(email = "save$n@test.com", passwordHash = "x", name = "u$n", phone = "010", nickname = "저장$n"))
    private fun site(name: String) = em.persist(ObservationSite(name = name, latitude = 37.0, longitude = 127.0))

    private fun savedCount(userId: Long, siteId: Long): Int =
        (em.entityManager.createNativeQuery("select count(*) from user_saved_sites where user_id = :u and site_id = :s")
            .setParameter("u", userId).setParameter("s", siteId).singleResult as Number).toInt()

    private fun v9Statements(): List<String> =
        File("src/main/resources/db/migration/V9__user_saved_sites_unique_user_site.sql").readLines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    @Test
    fun `엔티티가 만든 스키마에도 UNIQUE 가 있어 같은 관측지를 두 번 저장할 수 없다`() {
        val u = user(1); val s = site("별마로")
        em.persist(UserSavedSite(user = u, site = s))
        em.flush()
        assertThatThrownBy {
            em.persist(UserSavedSite(user = u, site = s))
            em.flush()
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `V9 는 중복 중 가장 먼저 저장된 행만 남기고 UNIQUE 를 건다(임의 좌표·다른 사용자는 그대로)`() {
        // 운영처럼 제약이 없던 상태를 만든다(테스트 스키마는 엔티티에서 와서 이미 제약이 있다)
        em.entityManager.createNativeQuery("ALTER TABLE user_saved_sites DROP CONSTRAINT uk_user_saved_sites_user_site").executeUpdate()
        val me = user(2); val other = user(3); val s = site("천문대")
        val first = em.persist(UserSavedSite(user = me, site = s))
        repeat(2) { em.persist(UserSavedSite(user = me, site = s)) }
        em.persist(UserSavedSite(user = other, site = s))
        repeat(2) { em.persist(UserSavedSite(user = me, customName = "집", customLatitude = 37.1, customLongitude = 127.1)) }
        em.flush()
        assertThat(savedCount(me.id, s.id!!)).isEqualTo(3)

        val sql = v9Statements()
        assertThat(sql).hasSize(2)
        sql.forEach { em.entityManager.createNativeQuery(it).executeUpdate() }
        em.clear()

        assertThat(savedCount(me.id, s.id!!)).isEqualTo(1)
        assertThat(em.find(UserSavedSite::class.java, first.id)).isNotNull()  // 가장 먼저 저장된 행이 남는다
        assertThat(savedCount(other.id, s.id!!)).isEqualTo(1)
        val custom = (em.entityManager.createNativeQuery("select count(*) from user_saved_sites where user_id = :u and site_id is null")
            .setParameter("u", me.id).singleResult as Number).toInt()
        assertThat(custom).isEqualTo(2)  // site_id NULL 은 UNIQUE 대상이 아니다
        assertThatThrownBy {
            em.entityManager.createNativeQuery("insert into user_saved_sites (user_id, site_id) values (:u, :s)")
                .setParameter("u", me.id).setParameter("s", s.id).executeUpdate()
        }.isInstanceOf(Exception::class.java)
    }

    @Test
    fun `관측지별 후기 조회는 그 관측지에 연결된 후기만 최신순으로 준다`() {
        val author = user(4); val target = site("목표"); val elsewhere = site("다른곳")
        fun review(title: String, at: com.project.byeoldori.observationsites.entity.ObservationSite?) {
            val p = em.persist(CommunityPost(author = author, type = PostType.REVIEW, title = title, content = "내용"))
            em.persist(ReviewPost(post = p, observationSite = at))
        }
        review("첫 후기", target)
        review("다른 관측지", elsewhere)
        review("관측지 없음", null)
        review("둘째 후기", target)
        em.persist(CommunityPost(author = author, type = PostType.FREE, title = "자유글", content = "내용"))
        em.flush(); em.clear()

        val page = postRepo.findReviewsBySiteId(
            target.id!!, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        )

        assertThat(page.totalElements).isEqualTo(2)
        assertThat(page.content.map { it.title }).containsExactly("둘째 후기", "첫 후기")
    }
}
