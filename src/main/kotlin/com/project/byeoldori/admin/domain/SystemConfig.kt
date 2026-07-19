package com.project.byeoldori.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 키-값 확장형 시스템 전역 설정 (MySQL).
 * key/value 는 MySQL 예약어이므로 컬럼명은 config_key / config_value 로 매핑한다.
 */
@Entity
@Table(name = "system_config")
class SystemConfig(
    @Id
    @Column(name = "config_key", length = 128)
    var key: String = "",

    @Column(name = "config_value", length = 1024, nullable = false)
    var value: String = ""
)
