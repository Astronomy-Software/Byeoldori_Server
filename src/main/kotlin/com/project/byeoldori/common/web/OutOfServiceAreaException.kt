package com.project.byeoldori.common.web

import com.project.byeoldori.common.exception.ByeoldoriException
import com.project.byeoldori.common.exception.ErrorCode
import com.project.byeoldori.forecast.utils.region.GeoBounds

class OutOfServiceAreaException : ByeoldoriException(
    errorCode = ErrorCode.OUT_OF_SERVICE_AREA,
    message = "한국 내 좌표만 지원합니다. (위도: ${GeoBounds.LAT_MIN}~${GeoBounds.LAT_MAX}, 경도: ${GeoBounds.LON_MIN}~${GeoBounds.LON_MAX})"
)