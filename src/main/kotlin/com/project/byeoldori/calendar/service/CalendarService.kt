
package com.project.byeoldori.calendar.service

import com.project.byeoldori.calendar.entity.*
import com.project.byeoldori.calendar.dto.*
import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.common.exception.InvalidInputException
import com.project.byeoldori.common.exception.NotFoundException
import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.community.common.service.StorageService
import com.project.byeoldori.observationsites.repository.ObservationSiteRepository
import com.project.byeoldori.user.entity.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class CalendarService(
    private val events: ObservationEventRepository,
    private val photos: CalendarImageRepository,
    private val storage: StorageService,
    private val siteRepo: ObservationSiteRepository
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${calendar.max-photos-per-event:10}")
    private val maxPhotosPerEvent: Int = 10

    @Transactional
    fun create(user: User, req: CreateEventRequest): Long {
        val startAtProcessed = req.startAt.withSecond(0).withNano(0)
        val endAtProcessed = req.endAt?.withSecond(0)?.withNano(0)

        if (req.endAt != null && req.endAt.isBefore(req.startAt)) {
            throw InvalidInputException(ErrorCode.INVALID_TIME_RANGE.message)
        }

        val site = req.observationSiteId?.let {
            siteRepo.findById(it).orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
        }

        val e = ObservationEvent(
            userId = user.id,
            title = req.title,
            startAt = startAtProcessed,
            endAt = if (req.status == EventStatus.COMPLETED) {
                endAtProcessed ?: startAtProcessed
            } else {
                endAtProcessed
            },
            targetName = req.targetName,
            observationSite = site,
            lat = site?.latitude ?: req.lat,
            lon = site?.longitude ?: req.lon,
            placeName = site?.name ?: req.placeName,
            status = req.status,
            memo = req.memo
        )

        val saved = events.save(e)

        val urls = req.imageUrls.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (urls.isNotEmpty()) {
            val curCount: Long = photos.countByEventId(saved.id!!)
            val room: Int = maxPhotosPerEvent - curCount.toInt()
            if (room <= 0) return saved.id!!
            val toAttach = if (urls.size > room) urls.take(room) else urls

            val newImages = toAttach.map { u ->
                CalendarImage(event = saved, url = u, contentType = null)
            }
            photos.saveAll(newImages)
        }
        return saved.id!!
    }

    @Transactional(readOnly = true)
    fun listByDate(user: User, date: LocalDate): List<EventResponse> {
        val from = date.atStartOfDay()
        val to = date.plusDays(1).atStartOfDay()
        val eventList = events.findAllByUserIdAndStartAtBetweenOrderByStartAtAsc(user.id, from, to)
        return assemble(eventList)
    }

    private fun assemble(eventList: List<ObservationEvent>): List<EventResponse> {
        if (eventList.isEmpty()) return emptyList()

        val ids = eventList.mapNotNull { it.id }
        val photosMap = photos.findAllByEventIdIn(ids)
            .sortedWith(compareBy<CalendarImage>({ it.event.id }, { it.id }))
            .groupBy { it.event.id }

        return eventList.map { ev ->
            EventResponse.from(ev, photosMap[ev.id] ?: emptyList())
        }
    }

    @Transactional(readOnly = true)
    fun get(user: User, id: Long): EventResponse {
        val e = findEventByIdAndUser(id, user)
        val images = photos.findAllByEventIdOrderByIdAsc(e.id!!)
        return EventResponse.from(e, images)
    }

    @Transactional
    fun update(user: User, id: Long, req: UpdateEventRequest): EventResponse {
        val e = findEventByIdAndUser(id, user)

        val startAtProcessed = req.startAt?.withSecond(0)?.withNano(0)
        val endAtProcessed = req.endAt?.withSecond(0)?.withNano(0)

        req.endAt?.let {
            val start = req.startAt ?: e.startAt
            if (it.isBefore(start)) throw InvalidInputException(ErrorCode.INVALID_TIME_RANGE.message)
        }

        req.title?.let { e.title = it }
        startAtProcessed?.let { e.startAt = it }
        endAtProcessed?.let { e.endAt = it }
        req.targetName?.let { e.targetName = it }

        if (req.observationSiteId != null) {
            if (req.observationSiteId <= 0L) {
                e.observationSite = null
                req.lat?.let { e.lat = it }
                req.lon?.let { e.lon = it }
                req.placeName?.let { e.placeName = it }
            } else {
                val site = siteRepo.findById(req.observationSiteId)
                    .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
                e.observationSite = site
                e.lat = site.latitude
                e.lon = site.longitude
                e.placeName = site.name
            }
        } else {
            req.lat?.let { e.lat = it }
            req.lon?.let { e.lon = it }
            req.placeName?.let { e.placeName = it }
        }
        req.memo?.let { e.memo = it }
        req.status?.let { e.status = it }

        // 1) 제거 요청 처리 (기존 로직 유지)
        val currentImages = req.removeImageIds?.let { ids ->
            val exists = photos.findAllByEventIdOrderByIdAsc(e.id!!)
            val toDelete = exists.filter { it.id != null && ids.contains(it.id) }

            photos.deleteAll(toDelete)
            toDelete.forEach { img ->
                try { storage.deleteImageByUrl(img.url) } catch (_: Exception) { }
            }

            exists.filterNot { toDelete.contains(it) }
        } ?: photos.findAllByEventIdOrderByIdAsc(e.id!!)

        req.addImageUrls?.let { urls ->
            val cleaned = urls
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val existingUrls = currentImages.map { it.url }.toSet()
            val toAttach = cleaned.filterNot { existingUrls.contains(it) }

            val room = maxPhotosPerEvent - currentImages.size
            if (toAttach.size > room) {
                throw InvalidInputException(ErrorCode.MAX_IMAGE_COUNT_EXCEEDED.message)
            }

            val newImages = toAttach.map { u -> CalendarImage(event = e, url = u, contentType = null) }
            if (newImages.isNotEmpty()) photos.saveAll(newImages)
        }
        val resultImages = photos.findAllByEventIdOrderByIdAsc(e.id!!)
        return EventResponse.from(e, resultImages)
    }

    @Transactional
    fun delete(user: User, id: Long) {
        val e = findEventByIdAndUser(id, user)

        val children = photos.findAllByEventIdOrderByIdAsc(e.id!!)
        val urlsToDelete = children.map { it.url }

        if (children.isNotEmpty()) {
            photos.deleteAll(children)
        }

        events.delete(e)

        urlsToDelete.forEach { url ->
            try { storage.deleteImageByUrl(url) } catch (ex: Exception) {
                logger.warn("스토리지 파일 삭제 실패 (Event ID: ${e.id}, URL: $url): {}", ex.message)
            }
        }
    }

    @Transactional
    fun complete(user: User, id: Long, observedAt: LocalDateTime?): EventResponse {
        val e = findEventByIdAndUser(id, user)
        e.status = EventStatus.COMPLETED

        val end = observedAt?.withSecond(0)?.withNano(0)
            ?: e.endAt?.withSecond(0)?.withNano(0)
            ?: e.startAt.withSecond(0).withNano(0)
        e.endAt = end

        val images = photos.findAllByEventIdOrderByIdAsc(e.id!!)
        return EventResponse.from(e, images)
    }

    @Transactional(readOnly = true)
    fun monthlySummary(user: User, year: Int, month: Int): List<DaySummary> {
        val ym = YearMonth.of(year, month)
        val from = ym.atDay(1).atStartOfDay()
        val to = ym.plusMonths(1).atDay(1).atStartOfDay()

        val rows = events.findStartStatusByUserIdAndStartAtBetween(user.id, from, to)

        val map = linkedMapOf<LocalDate, DaySummary>()
        rows.forEach { r ->
            val d = r.startAt.toLocalDate()
            val cur = map[d] ?: DaySummary(d)
            map[d] = when (r.status) {
                EventStatus.PLANNED   -> cur.copy(planned = cur.planned + 1)
                EventStatus.COMPLETED -> cur.copy(completed = cur.completed + 1)
                EventStatus.CANCELED  -> cur.copy(canceled = cur.canceled + 1)
            }
        }
        return map.values.sortedBy { it.date }
    }

    private fun findEventByIdAndUser(id: Long, user: User): ObservationEvent {
        return events.findByIdAndUserId(id, user.id)
            .orElseThrow { NotFoundException(ErrorCode.EVENT_NOT_FOUND, ErrorCode.EVENT_NOT_FOUND.message) }
    }
}