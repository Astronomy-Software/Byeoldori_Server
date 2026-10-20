-- V4: 탈퇴(소프트 딜리트) 계정이 원래 이메일·실명을 계속 점유하던 문제 정리 (데이터만 바꾸고 스키마는 그대로).
-- 예전 탈퇴 처리는 users.email(UNIQUE)·name 을 남겨 같은 이메일로 재가입하면 409, 소셜 로그인은
-- 탈퇴 행에 다시 연결돼 쓸 수 없는 토큰이 발급됐다. 이제 탈퇴 시 UserService.deleteAccount 가
-- email 을 DeletedAccount.tombstoneEmail(id) 형식으로 바꾸므로, 이전에 탈퇴한 계정에도 같은 형식을 적용한다.
UPDATE `users`
SET `email` = CONCAT('deleted+', `id`, '@deleted.byeoldori'),
    `name`  = '탈퇴한 사용자'
WHERE `deleted_at` IS NOT NULL
  AND `email` NOT LIKE '%@deleted.byeoldori';
