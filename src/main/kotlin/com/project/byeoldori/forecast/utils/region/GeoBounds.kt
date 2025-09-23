package com.project.byeoldori.forecast.utils.region

object GeoBounds {
    const val LAT_MIN = 33.0
    const val LAT_MAX = 38.7
    const val LON_MIN = 124.0
    const val LON_MAX = 132.0

    fun isInKorea(latitude: Double, longitude: Double): Boolean =
        latitude in LAT_MIN..LAT_MAX && longitude in LON_MIN..LON_MAX
}