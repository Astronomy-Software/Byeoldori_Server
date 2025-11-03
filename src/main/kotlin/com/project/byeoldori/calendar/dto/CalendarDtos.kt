package com.project.byeoldori.calendar.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import com.project.byeoldori.calendar.entity.CalendarImage
import com.project.byeoldori.calendar.entity.EventStatus
import com.project.byeoldori.calendar.entity.ObservationEvent
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.time.LocalDateTime

data class CreateEventRequest(
    @field:NotBlank val title: String,
    @field:NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val startAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val endAt: LocalDateTime? = null,
    val observationSiteId: Long? = null,
    val targets: List<String>? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val memo: String? = null,
    val status: EventStatus = EventStatus.PLANNED, // COMPLETED는 '기록'
    @JsonAlias("addImageUrls", "imageUrls", "photos")
    val imageUrls: List<String>? = null

)

data class UpdateEventRequest(
    val title: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val startAt: LocalDateTime? = null,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val endAt: LocalDateTime? = null,
    val targets: List<String>? = null,
    val observationSiteId: Long? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val memo: String? = null,
    val status: EventStatus? = null,
    val removeImageIds: List<Long>? = null,
    @JsonAlias("addPhotoUrls", "addImages", "addImageUrls")
    val addImageUrls: List<String>? = null
)

data class PhotoResponse(
    val id: Long,
    val url: String,
    val contentType: String?
)

data class EventResponse(
    val id: Long,
    val title: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val startAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    val endAt: LocalDateTime?,
    val targets: List<String> = emptyList(),
    val observationSiteId: Long?,
    val observationSiteName: String?,
    val lat: Double?, val lon: Double?, val placeName: String?,
    val status: EventStatus, val memo: String?,
    val photos: List<PhotoResponse>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?

) {
    companion object {
        fun from(e: ObservationEvent, images: List<CalendarImage>, targets: List<String>): EventResponse {
            return EventResponse(
                id = e.id!!, title = e.title, startAt = e.startAt, endAt = e.endAt,
                targets = targets, lat = e.lat, lon = e.lon,
                placeName = e.placeName, status = e.status, memo = e.memo,
                photos = images.map { PhotoResponse(it.id!!, it.url, it.contentType) },
                createdAt = e.createdAt, updatedAt = e.updatedAt,
                observationSiteId = e.observationSite?.id,
                observationSiteName = e.observationSite?.name
            )
        }
    }
}

data class DaySummary(
    val date: LocalDate,
    val planned: Long = 0,
    val completed: Long = 0,
    val canceled: Long = 0
)