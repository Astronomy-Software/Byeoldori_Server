package com.project.byeoldori.star.repository

import com.project.byeoldori.star.entity.Star
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StarRepository : JpaRepository<Star, String> {
    fun findByObjectName(name: String): Star?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Star s set s.reviewCount = s.reviewCount + 1 where s.objectName in :names")
    fun incReview(@Param("names") names: List<String>): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Star s set s.educationCount = s.educationCount + 1 where s.objectName in :names")
    fun incEducation(@Param("names") names: List<String>): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Star s set s.reviewCount = s.reviewCount - 1 where s.objectName in :names and s.reviewCount > 0")
    fun decReview(@Param("names") names: List<String>): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Star s set s.educationCount = s.educationCount - 1 where s.objectName in :names and s.educationCount > 0")
    fun decEducation(@Param("names") names: List<String>): Int
}