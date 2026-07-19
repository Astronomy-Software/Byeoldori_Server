package com.project.byeoldori.admin.repository

import com.project.byeoldori.admin.domain.SystemConfig
import org.springframework.data.jpa.repository.JpaRepository

interface SystemConfigRepository : JpaRepository<SystemConfig, String>
