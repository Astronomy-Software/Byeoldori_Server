-- V3: 비밀번호 재설정 토큰 테이블 추가
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
