-- V1 baseline: 현재 운영 스키마 스냅샷 (2026-07-06, OCI MySQL 8.0에서 mysqldump --no-data)
-- 기존 V2~V6는 이 baseline에 squash됨. 신규 빈 DB는 이 스크립트로 전체 스키마 생성.
-- 기존 운영 DB(flyway 이력 없음)는 baseline-on-migrate + baseline-version=1로 v1 마킹만 됨.
SET FOREIGN_KEY_CHECKS=0;


CREATE TABLE `calendar_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content_type` varchar(255) DEFAULT NULL,
  `url` varchar(512) NOT NULL,
  `event_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_photo_event` (`event_id`),
  CONSTRAINT `FKb4td9itswyh61hs19mdo7oyp0` FOREIGN KEY (`event_id`) REFERENCES `observation_event` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `depth` int NOT NULL,
  `like_count` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKir20vhrx08eh4itgpbfxip0s1` (`author_id`),
  KEY `FKde3rfu96lep00br5ov0mdieyt` (`parent_id`),
  KEY `FK42prch0kljv1paxtuc3uvxk` (`post_id`),
  CONSTRAINT `FK42prch0kljv1paxtuc3uvxk` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKde3rfu96lep00br5ov0mdieyt` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`),
  CONSTRAINT `FKir20vhrx08eh4itgpbfxip0s1` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `comment_likes` (
  `comment_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`comment_id`,`user_id`),
  KEY `FK6h3lbneryl5pyb9ykaju7werx` (`user_id`),
  CONSTRAINT `FK6h3lbneryl5pyb9ykaju7werx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKd0epu3dcjc57pwe7lt5jgfqsi` FOREIGN KEY (`comment_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `community` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `comment_count` bigint NOT NULL,
  `content` tinytext NOT NULL,
  `like_count` bigint NOT NULL,
  `title` varchar(120) NOT NULL,
  `type` enum('EDUCATION','FREE','REVIEW') NOT NULL,
  `view_count` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlfj9f611hplrh8cnrkxxtb1au` (`author_id`),
  CONSTRAINT `FKlfj9f611hplrh8cnrkxxtb1au` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `content_target` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_id` bigint NOT NULL,
  `content_type` enum('EDUCATION','EVENT','REVIEW') NOT NULL,
  `sort_order` int NOT NULL,
  `star_object_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ct_type_id_star` (`content_type`,`content_id`,`star_object_name`),
  KEY `ix_ct_type_id` (`content_type`,`content_id`),
  KEY `ix_ct_star` (`star_object_name`),
  KEY `ix_ct_type_star` (`content_type`,`star_object_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `education_post` (
  `post_id` bigint NOT NULL,
  `average_score` double NOT NULL,
  `content_url` varchar(1024) DEFAULT NULL,
  `difficulty` enum('ADVANCED','BEGINNER','INTERMEDIATE') DEFAULT NULL,
  `rating_count` bigint NOT NULL,
  `status` enum('DRAFT','PUBLISHED') NOT NULL,
  `tags` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `FK3vcy7j5btemkgr13e2txjpx38` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `education_rating` (
  `education_post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `cons` text,
  `created_at` datetime(6) DEFAULT NULL,
  `pros` text,
  `score` int NOT NULL,
  PRIMARY KEY (`education_post_id`,`user_id`),
  KEY `FKmi7501hvqjtc7d6wr9usk0rf5` (`user_id`),
  CONSTRAINT `FK9a2cowdq2syxj3flhtnrf04aj` FOREIGN KEY (`education_post_id`) REFERENCES `education_post` (`post_id`),
  CONSTRAINT `FKmi7501hvqjtc7d6wr9usk0rf5` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `email_verification_tokens` (
  `id` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKi1c4mmamlb8keqt74k4lrtwhc` (`user_id`),
  CONSTRAINT `FKi1c4mmamlb8keqt74k4lrtwhc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `free_post` (
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`post_id`),
  CONSTRAINT `FKdf21ptxsnokq5q2pr5ox6oak1` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `likes` (
  `post_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`post_id`,`user_id`),
  KEY `FKnvx9seeqqyy71bij291pwiwrg` (`user_id`),
  CONSTRAINT `FKfjbs1i0nfrwjghs4v95i24v5u` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKnvx9seeqqyy71bij291pwiwrg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mid_combined_forecast` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `do_reg_id` varchar(255) DEFAULT NULL,
  `max` int DEFAULT NULL,
  `min` int DEFAULT NULL,
  `pre` varchar(255) DEFAULT NULL,
  `rn_st` int DEFAULT NULL,
  `si_reg_id` varchar(255) DEFAULT NULL,
  `sky` varchar(255) DEFAULT NULL,
  `tm_ef` varchar(255) DEFAULT NULL,
  `tm_fc` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4fyho3oomxphirwa3uuxcirdi` (`tm_fc`,`tm_ef`,`si_reg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mid_forecast` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `pre` varchar(255) NOT NULL,
  `reg_id` varchar(255) NOT NULL,
  `rn_st` int NOT NULL,
  `sky` varchar(255) NOT NULL,
  `tm_ef` varchar(255) NOT NULL,
  `tm_fc` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `mid_temp_forecast` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `max` int NOT NULL,
  `min` int NOT NULL,
  `reg_id` varchar(255) NOT NULL,
  `tm_ef` varchar(255) NOT NULL,
  `tm_fc` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `body` varchar(512) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `title` varchar(128) NOT NULL,
  `type` enum('COMMENT_LIKED','NEW_COMMENT','SYSTEM') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9y21adhxn0ayjhfocscqox7bh` (`user_id`),
  CONSTRAINT `FK9y21adhxn0ayjhfocscqox7bh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `observation_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `end_at` datetime(6) DEFAULT NULL,
  `lat` double DEFAULT NULL,
  `lon` double DEFAULT NULL,
  `memo` text,
  `place_name` varchar(255) DEFAULT NULL,
  `start_at` datetime(6) NOT NULL,
  `status` enum('CANCELED','COMPLETED','PLANNED') NOT NULL,
  `title` varchar(120) NOT NULL,
  `user_id` bigint NOT NULL,
  `observation_site_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_event_user_start` (`user_id`,`start_at`),
  KEY `FKn4ve3wbnuplwhqbviwbri70mx` (`observation_site_id`),
  CONSTRAINT `FKn4ve3wbnuplwhqbviwbri70mx` FOREIGN KEY (`observation_site_id`) REFERENCES `observation_site` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `observation_site` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `latitude` double NOT NULL,
  `longitude` double NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `password_reset_tokens` (
  `id` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKk3ndxg5xp6v7wd4gjyusp15gq` (`user_id`),
  CONSTRAINT `FKk3ndxg5xp6v7wd4gjyusp15gq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `post_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `sort_order` int NOT NULL,
  `url` varchar(512) NOT NULL,
  `post_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb88ky9jhe9cfmeft8ijy8s183` (`post_id`),
  CONSTRAINT `FKb88ky9jhe9cfmeft8ijy8s183` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `rotated_at` datetime(6) DEFAULT NULL,
  `token_hash` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7tdcd6ab5wsgoudnvj7xf1b7l` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `review_post` (
  `post_id` bigint NOT NULL,
  `equipment` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `observation_dt` date DEFAULT NULL,
  `score` int DEFAULT NULL,
  `observation_site_id` bigint DEFAULT NULL,
  PRIMARY KEY (`post_id`),
  KEY `FKatehaa8gms1f3t30xo38xywhs` (`observation_site_id`),
  CONSTRAINT `FKatehaa8gms1f3t30xo38xywhs` FOREIGN KEY (`observation_site_id`) REFERENCES `observation_site` (`id`),
  CONSTRAINT `FKiuucbyqh89r7wlnqkvbehktxf` FOREIGN KEY (`post_id`) REFERENCES `community` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `user_roles` (
  `user_id` bigint NOT NULL,
  `role` varchar(255) DEFAULT NULL,
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `user_saved_sites` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `custom_latitude` double DEFAULT NULL,
  `custom_longitude` double DEFAULT NULL,
  `custom_name` varchar(255) DEFAULT NULL,
  `saved_at` datetime(6) DEFAULT NULL,
  `site_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfxa6600yb8vse2s27knqg6hda` (`site_id`),
  KEY `FKfjs1xq8la9d98rhq1r6lt2dej` (`user_id`),
  CONSTRAINT `FKfjs1xq8la9d98rhq1r6lt2dej` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKfxa6600yb8vse2s27knqg6hda` FOREIGN KEY (`site_id`) REFERENCES `observation_site` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `birthdate` date DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(255) NOT NULL,
  `profile_image_url` varchar(1024) DEFAULT NULL,
  `provider` varchar(32) DEFAULT NULL,
  `provider_id` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK2ty1xmrrgtn89xt7kyxx6ta7h` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


SET FOREIGN_KEY_CHECKS=1;
