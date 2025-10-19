
package com.project.byeoldori.calendar.service

import com.project.byeoldori.calendar.entity.*
import com.project.byeoldori.calendar.dto.*
import com.project.byeoldori.calendar.repository.CalendarImageRepository
import com.project.byeoldori.calendar.repository.ObservationEventRepository
import com.project.byeoldori.common.exception.ConflictException
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
import org.springframework.web.multipart.MultipartFile
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
    @Value("\${calendar.max-image-bytes:10485760}")
    private val maxBytesPerImage: Long = 10L * 1024 * 1024

    @Transactional
    fun create(user: User, req: CreateEventRequest): Long {
        val startAtProcessed = req.startAt.withSecond(0).withNano(0)
        val endAtProcessed = req.endAt?.withSecond(0)?.withNano(0)

        if (req.endAt != null && req.endAt.isBefore(req.startAt)) {
            throw InvalidInputException(ErrorCode.INVALID_TIME_RANGE.message)
        }

        val site = req.observationSiteId?.let {
            siteRepo.findById(it)
                .orElseThrow { NotFoundException(ErrorCode.SITE_NOT_FOUND) }
        }

        val e = ObservationEvent(
            userId = user.id,
            title = req.title,
            startAt = startAtProcessed,
            endAt = if (req.status == EventStatus.COMPLETED){ endAtProcessed ?: startAtProcessed }
            else { endAtProcessed },
            targetName = req.targetName,
            observationSite = site,
            lat = site?.latitude ?: req.lat,
            lon = site?.longitude ?: req.lon,
            placeName = site?.name ?: req.placeName,
            status = req.status,
            memo = req.memo
        )
        return events.save(e).id!!
    }

    @Transactional(readOnly = true)
    fun listByDate(user: User, date: LocalDate): List<EventResponse> {
        val from = date.atStartOfDay()
            val to = date.plusDays(1).atStartOfDay()

        val eventList = events.findAllByUserIdAndStartAtBetweenOrderByStartAtAsc(user.id, from, to)
        if (eventList.isEmpty()) {
            return emptyList()
        }

        val eventIds = eventList.mapNotNull { it.id }

        val allPhotos = photos.findAllByEventIdIn(eventIds)
        val photosMap = allPhotos.groupBy { it.event.id }

        return eventList.map { event ->
            val eventPhotos = photosMap[event.id] ?: emptyList()
            EventResponse.from(event, eventPhotos)
        }
    }

    @Transactional(readOnly = true)
    fun list(user: User, fromDate: LocalDate, toDate: LocalDate): List<EventResponse> {
        val from = fromDate.atStartOfDay()
        val to = toDate.plusDays(1).atStartOfDay()

        val eventList = events.findAllByUserIdAndStartAtBetweenOrderByStartAtAsc(user.id, from, to)
        if (eventList.isEmpty()) {
            return emptyList()
        }
        val eventIds = eventList.mapNotNull { it.id }
        val allPhotos = photos.findAllByEventIdIn(eventIds)
        val photosMap = allPhotos.groupBy { it.event.id }
        return eventList.map { event ->
            val eventPhotos = photosMap[event.id] ?: emptyList()
            EventResponse.from(event, eventPhotos)
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

        val remainingImages = req.removeImageIds?.let { ids ->
            val exists = photos.findAllByEventIdOrderByIdAsc(e.id!!)
            val toDelete = exists.filter { it.id != null && ids.contains(it.id) }

            photos.deleteAll(toDelete)

            toDelete.forEach { img ->
                try { storage.deleteImageByUrl(img.url) } catch (_: Exception) { }
            }

            exists.filterNot { toDelete.contains(it) }
        } ?: photos.findAllByEventIdOrderByIdAsc(e.id!!)

        return EventResponse.from(e, remainingImages)
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
    fun uploadPhotos(user: User, eventId: Long, files: List<MultipartFile>): List<PhotoResponse> {
        val e = findEventByIdAndUser(eventId, user)

        val current = photos.findAllByEventIdOrderByIdAsc(e.id!!).size
        if (current + files.size > maxPhotosPerEvent) {
            throw InvalidInputException(ErrorCode.MAX_IMAGE_COUNT_EXCEEDED.message)
        }

        files.forEach { f ->
            if (f.size > maxBytesPerImage) {
                throw ConflictException(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.message)
            }
        }

        val saved = files.map { f ->
            val url = storage.storeImage(f)
            photos.save(CalendarImage(event = e, url = url, contentType = f.contentType))
        }
        return saved.map { PhotoResponse(id = it.id!!, url = it.url, contentType = it.contentType) }
    }

    @Transactional
    fun complete(user: User, id: Long, observedAt: LocalDateTime?): EventResponse {
        val e = findEventByIdAndUser(id, user)
        e.status = EventStatus.COMPLETED
        val observedAtProcessed = observedAt?.withSecond(0)?.withNano(0)
        e.endAt = observedAtProcessed ?: e.startAt.withSecond(0).withNano(0)
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