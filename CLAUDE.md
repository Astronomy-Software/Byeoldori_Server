# 별도리 Server - Claude 규칙

## 프로젝트 개요

- **프로젝트**: 별도리(Byeoldori) 천체 관측 교육 앱 백엔드 서버
- **기술 스택**: Kotlin, Spring Boot 3.4.3, JDK 21, MySQL 8.0, Redis
- **루트 패키지**: `com.project.byeoldori`
- **아키텍처**: 레이어드 아키텍처 (controller / service / repository / entity / dto)

## 패키지 구조

```
com.project.byeoldori
├── calendar        # 관측 캘린더
├── common          # 공통 (exception, jpa, logging, web)
├── community       # 커뮤니티 (post, comment, like)
├── config          # 전역 설정
├── forecast        # 기상 예보 (기상청 API, Quartz Scheduler)
├── notification    # 알림
├── observationsites # 관측지
├── security        # 인증/인가 (JWT, OAuth2)
├── star            # 천문 계산 (달 위상, 관측 적합도)
└── user            # 사용자
```

## 브랜치 전략

- `main`: production 배포용 — 직접 커밋 금지
- `develop`: 개발/테스트용 — 기능 완성 후 main으로 병합
- `feature/기능명`: 기능 단위로 브랜치 생성 → 완료 후 develop에 PR

## 작업 규칙

- 새 기능은 반드시 `feature/` 브랜치에서 시작한다.
- `main` 브랜치에 직접 push하지 않는다.
- 코드 수정 전 반드시 해당 파일을 먼저 읽는다.
- 새 도메인 추가 시 기존 패키지 구조(controller/service/repository/entity/dto)를 따른다.
- `git push --force`, `git reset --hard` 등 파괴적인 명령은 명시적 요청 없이 실행하지 않는다.
- JWT, OAuth2 등 보안 관련 코드 수정 시 반드시 사용자에게 먼저 알린다.
