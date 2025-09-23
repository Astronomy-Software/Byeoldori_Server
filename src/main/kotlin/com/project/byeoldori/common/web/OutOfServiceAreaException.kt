package com.project.byeoldori.common.web

class OutOfServiceAreaException(val latitude: Double, val longitude: Double)
    : RuntimeException("Out of service area: lat=$latitude, lon=$longitude")