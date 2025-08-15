package com.project.byeoldori.community.common.exception

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.OffsetDateTime

data class ApiError(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val details: Map<String, String?>? = null
)