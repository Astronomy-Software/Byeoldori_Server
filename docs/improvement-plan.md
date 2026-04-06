# 별도리 백엔드 개선 계획서

> 작성일: 2026-04-06  
> 분석 대상: Byeoldori_Server (Spring Boot 3.4.3 / Kotlin)

---

## 목차

1. [현황 요약](#1-현황-요약)
2. [보안](#2-보안)
3. [성능](#3-성능)
4. [기능 추가](#4-기능-추가)
5. [운영 / 인프라](#5-운영--인프라)
6. [테스트](#6-테스트)
7. [코드 품질](#7-코드-품질)
8. [우선순위 로드맵](#8-우선순위-로드맵)

---

## 1. 현황 요약

| 항목 | 현재 상태 | 평가 |
|---|---|---|
| 아키텍처 | Controller → Service → Repository 표준 구조 | ✅ 양호 |
| JWT 인증 | 구현 완료, Google OAuth2 연동 | ✅ 양호 |
| DB 트랜잭션 | @Transactional 적절히 사용 | ✅ 양호 |
| 캐싱 | Redis 인프라 있으나 코드 레벨 적용 전무 | ❌ 미적용 |
| Rate Limiting | 전혀 없음 | ❌ 위험 |
| 관리자 권한 | roles 필드만 있고 실제 권한 체계 없음 | ❌ 없음 |
| DB 마이그레이션 | ddl-auto=update (프로덕션 위험) | ⚠️ 위험 |
| 헬스체크 | Actuator 미설정 | ❌ 없음 |
| 테스트 커버리지 | 추정 5% 미만 | ❌ 매우 낮음 |
| 모니터링 | 없음 | ❌ 없음 |

---

## 2. 보안

### 2-1. Rate Limiting 부재 🔴

**현재 문제**  
로그인, 회원가입, 비밀번호 초기화, 이메일 발송 엔드포인트에 횟수 제한이 전혀 없다.  
브루트포스 공격, 이메일 스팸 공격에 그대로 노출된 상태.

**개선 방향**  
Spring Boot 필터 레벨에서 IP + 엔드포인트 조합으로 Redis 기반 Rate Limiter 구현.

```
로그인:            분당 5회
비밀번호 초기화:    1시간당 3회  
이메일 인증 재발송: 분당 1회
회원가입:          분당 3회
```

**작업 범위**: `RateLimitFilter.kt` 신규 추가, `SecurityConfig.kt` 필터 등록

---

### 2-2. 관리자 권한 시스템 부재 🔴

**현재 문제**  
`User` 엔티티에 `roles: MutableSet<String>` 필드가 있지만 실제로 사용되는 곳이 없다.  
관측지 등록/삭제, 게시글 강제 삭제 등 관리자 기능이 전혀 구현되어 있지 않다.

**개선 방향**

```kotlin
enum class Role { USER, ADMIN }
```

- `SecurityConfig`에 `@PreAuthorize("hasRole('ADMIN')")` 적용
- `AdminController` 신규 추가 (사용자 관리, 콘텐츠 강제 삭제, 통계)
- 관측지 등록/수정/삭제 → ADMIN 전용으로 제한

**작업 범위**: `User.kt`, `SecurityConfig.kt`, `AdminController.kt` 신규

---

### 2-3. 비밀번호 재설정 방식 취약 🟠

**현재 문제**  
임시 비밀번호를 이메일로 발송하는 방식. 임시 비밀번호가 이메일에 평문으로 존재한다.

**개선 방향**  
임시 비밀번호 대신 재설정 링크 토큰 방식으로 변경.

```
1. 재설정 요청 → DB에 토큰 저장 (만료 30분)
2. 이메일로 링크 발송 (/reset-password?token=xxx)
3. 링크 접속 → 새 비밀번호 입력 → 완료
```

**작업 범위**: `UserService.kt`, `EmailService.kt`, 이메일 템플릿 추가

---

### 2-4. JWT 필터 DB 직접 조회 🟡

**현재 문제**  
`JwtAuthenticationFilter`가 모든 인증 요청마다 `userRepo.findByEmail()` DB 쿼리를 실행한다.

**개선 방향**  
Redis에 사용자 정보를 캐시하여 DB 부하 감소.

```kotlin
// userEmail → User 객체를 Redis에 5분 캐시
@Cacheable(value = ["userCache"], key = "#email")
fun findUserByEmail(email: String): User?
```

탈퇴/정지 시 캐시 즉시 무효화(`@CacheEvict`) 처리 필요.

**작업 범위**: `JwtAuthenticationFilter.kt`, `UserService.kt`

---

## 3. 성능

### 3-1. 기상 데이터 캐싱 미적용 🔴

**현재 문제**  
`ForeCastService.getForecastDataByLocation()` 호출 시마다 DB 조회 + 광공해 점수 계산이 실행된다.  
같은 좌표에 대한 반복 요청도 매번 동일한 연산을 수행한다.

**개선 방향**

```kotlin
// 중기 예보: siRegId 기준 30분 캐시
@Cacheable(value = ["midForecast"], key = "#siRegId")
fun findBySiRegId(siRegId: String): List<MidCombinedForecastDTO>

// 광공해 점수: 좌표 기준 캐시 (변하지 않는 데이터)
@Cacheable(value = ["lightPollution"], key = "#lat + ',' + #lon")
fun getLightPollutionScore(lat: Double, lon: Double): Double
```

스케줄러에서 예보 데이터 갱신 시 관련 캐시 일괄 무효화.

**작업 범위**: `MidCombinedForecastService.kt`, `LightPollution.kt`, `application.properties`

---

### 3-2. 광공해 데이터 선형 탐색 O(n) 🟠

**현재 문제**  
`LightPollution.kt`가 CSV 전체 데이터(`pollutionData`)를 매 요청마다 선형 탐색한다.  
CSV 데이터가 수만 행이면 요청마다 수만 번 연산.

**개선 방향**  
애플리케이션 시작 시 GeoHash 또는 격자 인덱스로 전처리.

```kotlin
// 시작 시 5km 격자 기준 맵으로 변환
private val pollutionGrid: Map<String, Double> by lazy {
    loadCsv().associate { (lat, lon, value) ->
        geoHashEncode(lat, lon, precision = 4) to value
    }
}
```

**작업 범위**: `LightPollution.kt`

---

### 3-3. MidCombinedForecastService 저장 방식 비효율 🟡

**현재 문제**  
`saveAll()`에서 중복 데이터가 있을 경우 개별 delete → save 방식으로 처리.  
N건 저장 시 최대 N*2 쿼리 발생.

**개선 방향**  
`tmFc + tmEf + siRegId` 유니크 제약 추가 후 saveAll + ON DUPLICATE 방식으로 개선.  
또는 배치 단위로 삭제 후 일괄 saveAll.

**작업 범위**: `MidCombinedForecastService.kt`, DB 마이그레이션 스크립트

---

### 3-4. 관측지 전체 조회 페이지네이션 없음 🟡

**현재 문제**  
`GET /observationsites` → `findAll()` 전체 조회.  
관측지 수가 늘어나면 전체를 메모리에 로드.

**개선 방향**  
페이지네이션 파라미터 추가 또는 응답 개수 상한 설정.

**작업 범위**: `ObservationSiteController.kt`, `ObservationSiteService.kt`

---

## 4. 기능 추가

### 4-1. 푸시 알림 (FCM) 🟠

**필요 시나리오**
- 내 게시글에 댓글이 달렸을 때
- 내 댓글에 좋아요가 달렸을 때
- 관측 계획일 당일 날씨 예보 알림
- 이벤트 리마인더

**구현 방향**
- `firebase-admin` SDK 추가
- `FcmToken` 엔티티로 디바이스 토큰 관리
- `NotificationService` 신규 추가
- 알림 수신 여부를 유저 설정으로 관리

---

### 4-2. 이미지 최적화 🟡

**현재 문제**
- 썸네일 없이 원본 이미지 그대로 제공
- EXIF 데이터 제거 없음 (GPS 좌표 등 개인정보 포함 가능)
- WebP 변환 없음

**개선 방향**
- GCS 업로드 시 썸네일(400x400) 자동 생성
- EXIF 스트리핑 적용
- 원본/썸네일 URL 분리 제공

---

### 4-3. 검색 기능 개선 🟡

**현재 문제**  
`LIKE '%keyword%'` 방식으로 인덱스 미사용, 대용량 시 풀스캔.

**개선 방향**  
단기: MySQL FULLTEXT 인덱스 적용  
장기: Elasticsearch 도입 검토

---

### 4-4. 날씨 예보 위젯 API 🟡

**내용**  
현재 예보 API는 상세 데이터를 전부 반환한다.  
클라이언트(Unity)가 빠르게 표시할 수 있는 경량 요약 응답 추가.

```json
GET /weather/summary?lat=36.6&lon=127.4

{
  "suitability": 82,
  "sky": "맑음",
  "temperature": 14,
  "nextGoodTime": "2026-04-06T21:00"
}
```

---

## 5. 운영 / 인프라

### 5-1. DB 마이그레이션 도구 (Flyway) 🔴

**현재 문제**  
`spring.jpa.hibernate.ddl-auto=update`는 프로덕션에서 위험하다.  
스키마 버전 추적 불가, 롤백 불가.

**개선 방향**

```kotlin
// build.gradle.kts
implementation("org.flywaydb:flyway-mysql")

// application.properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.locations=classpath:db/migration
```

```
src/main/resources/db/migration/
  V001__init_schema.sql
  V002__add_role_column.sql
  V003__add_fcm_token_table.sql
```

---

### 5-2. 헬스체크 및 모니터링 🔴

**현재 문제**  
Cloud Run에서 헬스체크 엔드포인트가 없으면 장애 감지 불가.  
현재 GCP 로드밸런서가 사용할 헬스체크 경로 없음.

**개선 방향**

```kotlin
// build.gradle.kts
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.micrometer:micrometer-registry-prometheus")

// application.properties
management.endpoints.web.exposure.include=health,metrics,info
management.endpoint.health.show-details=never
```

Cloud Run에서 `/actuator/health`를 헬스체크 경로로 설정.

---

### 5-3. 환경별 설정 분리 🟠

**현재 문제**  
단일 `application.properties`에 개발/운영 설정이 혼재.  
`spring.jpa.show-sql=true`가 프로덕션에도 적용됨.

**개선 방향**

```
application.properties         → 공통 설정
application-local.properties   → 로컬 개발 (show-sql=true, ddl-auto=update)
application-prod.properties    → 프로덕션 (show-sql=false, ddl-auto=validate)
```

Cloud Run 환경변수: `SPRING_PROFILES_ACTIVE=prod`

---

### 5-4. 구조화된 로깅 🟡

**현재 문제**  
기본 텍스트 로그. 요청 추적 ID 없음. Cloud Logging에서 로그 검색 어려움.

**개선 방향**  
Logback + JSON 포맷 출력. 요청마다 MDC(Mapped Diagnostic Context)에 traceId 삽입.

```kotlin
// 필터에서 각 요청마다 ID 주입
MDC.put("traceId", UUID.randomUUID().toString().take(8))
```

---

## 6. 테스트

### 현황

| 대상 | 파일 수 | 테스트 유무 |
|---|---|---|
| UserService | 1 | ❌ |
| PostService | 1 | ❌ |
| CalendarService | 1 | ❌ |
| ForeCastService | 1 | ❌ |
| CommentService | 1 | ❌ |
| LightPollution | 1 | ✅ (상세) |
| ObservationSiteController | 1 | ✅ (기본) |
| WeatherController | 1 | ✅ (기본) |

추정 커버리지: **5% 미만**

### 개선 방향

**우선순위 1 — 핵심 서비스 단위 테스트**

```kotlin
// 예: UserServiceTest
@ExtendWith(MockitoExtension::class)
class UserServiceTest {
    @Test fun `회원가입 성공`()
    @Test fun `이메일 중복 시 ConflictException`()
    @Test fun `비밀번호 불일치 시 InvalidInputException`()
    @Test fun `로그인 성공 시 토큰 반환`()
    @Test fun `존재하지 않는 이메일 로그인 시 예외`()
}
```

**우선순위 2 — Controller 통합 테스트**

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Test fun `POST auth/login 성공 응답`()
    @Test fun `인증 없이 보호 엔드포인트 접근 시 401`()
}
```

**목표 커버리지**: 핵심 Service 레이어 70% 이상

---

## 7. 코드 품질

### 7-1. Entity 직접 반환 제거 🟡

**현재 문제**  
`ObservationSiteService.getAllSites()`, `CalendarService.get()` 등에서 Entity를 직접 반환.  
JPA 지연 로딩 관련 직렬화 오류 가능성, 민감 정보 노출 위험.

**개선 방향**  
모든 public API 응답을 DTO로 변환. Entity는 Service 내부에서만 사용.

---

### 7-2. Repository 쿼리 타입 안정성 🟡

**현재 문제**

```kotlin
// ReviewPostRepository.kt
fun findObservationSiteIdsByPostIds(postIds: List<Long>): List<Array<Any>>
// Array<Any> → 캐스팅 위험
```

**개선 방향**

```kotlin
data class SiteIdProjection(val postId: Long, val siteId: Long)

@Query("SELECT new ...SiteIdProjection(r.id, r.observationSite.id) ...")
fun findObservationSiteIdsByPostIds(postIds: List<Long>): List<SiteIdProjection>
```

---

### 7-3. application.properties 주석 정리 🟡

**현재 문제**

```properties
# gisangcheng fucking slow
spring.mvc.async.request-timeout=300000
```

프로덕션 코드에 부적절한 주석.

---

## 8. 우선순위 로드맵

### Phase 1 — 안정화 (즉시)

| 작업 | 난이도 | 기간 |
|---|---|---|
| Flyway DB 마이그레이션 도입 | 중 | 1일 |
| Actuator 헬스체크 활성화 | 하 | 1시간 |
| 환경별 설정 분리 (local/prod) | 하 | 2시간 |
| Rate Limiting 구현 | 중 | 2일 |
| application.properties 주석 정리 | 하 | 30분 |

---

### Phase 2 — 성능 (단기)

| 작업 | 난이도 | 기간 |
|---|---|---|
| 기상 데이터 Redis 캐싱 | 중 | 2일 |
| 광공해 탐색 최적화 | 중 | 1일 |
| JWT 필터 사용자 캐싱 | 중 | 1일 |
| 관측지 페이지네이션 추가 | 하 | 4시간 |

---

### Phase 3 — 보안 / 기능 (중기)

| 작업 | 난이도 | 기간 |
|---|---|---|
| 관리자 권한 시스템 | 상 | 5일 |
| 비밀번호 재설정 토큰 방식으로 개선 | 중 | 2일 |
| 핵심 서비스 단위 테스트 작성 | 상 | 1-2주 |
| Entity → DTO 분리 | 중 | 3일 |

---

### Phase 4 — 신기능 (장기)

| 작업 | 난이도 | 기간 |
|---|---|---|
| FCM 푸시 알림 | 상 | 1주 |
| 이미지 썸네일 / WebP 최적화 | 중 | 3일 |
| 날씨 예보 요약 API | 하 | 1일 |
| 검색 고도화 (MySQL FULLTEXT) | 중 | 2일 |
| 구조화된 로깅 (traceId) | 중 | 2일 |

---

*본 계획서는 코드 분석 결과를 기반으로 작성되었으며, 우선순위는 서비스 안정성 → 성능 → 보안 → 신기능 순으로 설정되었습니다.*
