package com.project.byeoldori.observationsites

import com.project.byeoldori.calendar.entity.ObservationEvent
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.community.common.domain.PostType
import com.project.byeoldori.community.post.domain.CommunityPost
import com.project.byeoldori.community.post.domain.ReviewPost
import com.project.byeoldori.community.post.repository.CommunityPostRepository
import com.project.byeoldori.community.post.repository.ReviewPostRepository
import com.project.byeoldori.observationsites.entity.ObservationSite
import com.project.byeoldori.observationsites.entity.UserSavedSite
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.observationsites.repository.UserSavedSiteRepository
import com.project.byeoldori.observationsites.service.ObservationSiteService
import com.project.byeoldori.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * 관측지 검색(LIKE)과 삭제(후기·캘린더 연결 해제)를 실제 JPA(H2 MySQL 모드)로 검증한다.
 * 예전에는 2글자 이상 검색이 FULLTEXT 인덱스 없는 MATCH 로 500, 연결된 관측지 삭제는 FK 위반으로 500 이었다.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ObservationSiteServiceJpaTest @Autowired constructor(
    private val em: TestEntityManager,
    private val siteRepo: ObservationSiteRepository,
    private val savedRepo: UserSavedSiteRepository,
    private val reviewRepo: ReviewPostRepository,
    private val postRepo: CommunityPostRepository,
    private val eventRepo: ObservationEventRepository,
) {
    private val service = ObservationSiteService(siteRepo, savedRepo, reviewRepo, postRepo, eventRepo)

    private fun site(name: String) = em.persist(ObservationSite(name = name, latitude = 37.0, longitude = 127.0))

    @Test
    fun `두 글자 이상 검색도 부분 일치로 찾는다`() {
        site("소백산 천문대")
        site("보현산 천문대")
        site("화천 조경철 천문대")
        site("영양 반딧불이 생태공원")
        em.flush(); em.clear()

        assertThat(service.searchByName("천문대").map { it.name })
            .containsExactlyInAnyOrder("소백산 천문대", "보현산 천문대", "화천 조경철 천문대")
        assertThat(service.searchByName("  보현산 ").map { it.name }).containsExactly("보현산 천문대")
        assertThat(service.searchByName("반").map { it.name }).containsExactly("영양 반딧불이 생태공원")
    }

    @Test
    fun `검색어의 퍼센트·BOOLEAN 연산자는 글자로 취급한다`() {
        site("소백산 천문대")
        em.flush(); em.clear()

        assertThat(service.searchByName("%")).isEmpty()
        assertThat(service.searchByName("+천문대 -소백")).isEmpty()
    }

    @Test
    fun `후기와 캘린더 기록이 연결된 관측지도 삭제되고 연결만 끊긴다`() {
        val user = em.persist(User(email = "site@test.com", passwordHash = "x", name = "s", phone = "010", nickname = "관측러"))
        val target = site("삭제될 관측지")
        val other = site("남는 관측지")

        val p1 = em.persist(CommunityPost(author = user, type = PostType.REVIEW, title = "후기1", content = "본문"))
        em.persist(ReviewPost(post = p1, observationSite = target, location = null, score = 5))
        val p2 = em.persist(CommunityPost(author = user, type = PostType.REVIEW, title = "후기2", content = "본문"))
        em.persist(ReviewPost(post = p2, observationSite = target, location = "직접 쓴 장소", score = 4))
        val p3 = em.persist(CommunityPost(author = user, type = PostType.REVIEW, title = "후기3", content = "본문"))
        em.persist(ReviewPost(post = p3, observationSite = other, score = 3))

        val ev = em.persist(
            ObservationEvent(
                userId = user.id, title = "관측", startAt = LocalDateTime.of(2026, 10, 1, 21, 0),
                observationSite = target, lat = 37.0, lon = 127.0, placeName = "삭제될 관측지"
            )
        )
        em.persist(UserSavedSite(user = user, site = target))
        em.flush(); em.clear()

        service.deleteSiteById(target.id)
        em.flush(); em.clear()

        assertThat(siteRepo.findById(target.id)).isEmpty
        val r1 = reviewRepo.findById(p1.id!!).get()
        assertThat(r1.observationSite).isNull()
        assertThat(r1.location).isEqualTo("삭제될 관측지") // 장소 이름은 남긴다
        assertThat(reviewRepo.findById(p2.id!!).get().location).isEqualTo("직접 쓴 장소") // 기존 값은 유지
        assertThat(reviewRepo.findById(p3.id!!).get().observationSite?.id).isEqualTo(other.id)
        val e = eventRepo.findById(ev.id!!).get()
        assertThat(e.observationSite).isNull()
        assertThat(e.placeName).isEqualTo("삭제될 관측지")
    }
}
