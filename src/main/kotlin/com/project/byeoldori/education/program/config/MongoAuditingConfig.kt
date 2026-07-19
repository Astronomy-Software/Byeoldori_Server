package com.project.byeoldori.education.program.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.EnableMongoAuditing

/**
 * @CreatedDate / @LastModifiedDate (EducationProgram) 감사 필드를 채우기 위한 설정.
 * 메인 애플리케이션에는 @EnableJpaAuditing 만 있어 Mongo 감사는 별도 활성화가 필요하다.
 */
@Configuration
@EnableMongoAuditing
class MongoAuditingConfig
