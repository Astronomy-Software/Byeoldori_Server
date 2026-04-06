-- V4: FCM 푸시 알림 토큰 테이블 추가
CREATE TABLE IF NOT EXISTS fcm_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token       VARCHAR(256) NOT NULL,
    device_type VARCHAR(16)  NOT NULL DEFAULT 'android',
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    UNIQUE KEY uk_fcm_token (token),
    CONSTRAINT fk_fcm_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
