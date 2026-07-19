package com.project.byeoldori.admin.service

import com.project.byeoldori.admin.domain.SystemConfig
import com.project.byeoldori.admin.repository.SystemConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SystemConfigService(
    private val repo: SystemConfigRepository
) {
    companion object {
        const val MODERATION_REQUIRED = "moderation.required"
    }

    @Transactional(readOnly = true)
    fun getBool(key: String, default: Boolean): Boolean {
        val cfg = repo.findById(key).orElse(null) ?: return default
        return cfg.value.trim().toBooleanStrictOrNull() ?: default
    }

    @Transactional
    fun set(key: String, value: String) {
        val cfg = repo.findById(key).orElse(null)
        if (cfg != null) {
            cfg.value = value
        } else {
            repo.save(SystemConfig(key = key, value = value))
        }
    }

    @Transactional(readOnly = true)
    fun getAll(): Map<String, String> =
        repo.findAll().associate { it.key to it.value }

    /** 발행 전 검수(moderation) 필요 여부. 미설정 시 기본 ON(true). */
    @Transactional(readOnly = true)
    fun moderationRequired(): Boolean = getBool(MODERATION_REQUIRED, true)
}
