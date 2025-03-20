package com.project.byeoldori.domain.midforecast

import jakarta.persistence.*

@Entity
@Table(name = "mid_forecast")
data class MidForecast(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val regId: String,

    @Column(nullable = false)
    val tmFc: String,

    @Column(nullable = false)
    val tmEf: String,

    @Column(nullable = false, name = "mod_code")
    val modCode: String,

    @Column(nullable = false)
    val stn: String,

    @Column(nullable = false)
    val c: String,

    @Column(nullable = false)
    val sky: String,

    @Column(nullable = false)
    val pre: String,

    @Column(nullable = false)
    val conf: String,

    @Column(nullable = false)
    val wf: String,

    @Column(nullable = false)
    val rnSt: Int
)