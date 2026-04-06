# 별도리 서버 API 온보딩 가이드

Unity 클라이언트 개발자를 위한 백엔드 API 연동 가이드입니다.

---

## 목차

1. [시작하기](#1-시작하기)
2. [인증 (Auth)](#2-인증-auth)
3. [유저 (User)](#3-유저-user)
4. [커뮤니티 - 게시글 (Post)](#4-커뮤니티---게시글-post)
5. [커뮤니티 - 댓글 (Comment)](#5-커뮤니티---댓글-comment)
6. [캘린더 (Calendar)](#6-캘린더-calendar)
7. [관측지 (Observation Sites)](#7-관측지-observation-sites)
8. [날씨/예보 (Weather)](#8-날씨예보-weather)
9. [별 / 천체 (Star)](#9-별--천체-star)
10. [알림 (Notifications)](#10-알림-notifications)

---

## 1. 시작하기

### Base URL

```
https://byeoldori-server-hbxnfn4woa-du.a.run.app
```

### Swagger UI

```
https://byeoldori-server-hbxnfn4woa-du.a.run.app/swagger-ui.html
```

### 공통 응답 형식

모든 API는 아래 형식으로 응답합니다.

```json
{
  "success": true,
  "message": "성공 메시지",
  "data": { ... }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | Boolean | 요청 성공 여부 |
| `message` | String | 결과 메시지 |
| `data` | Object / null | 실제 응답 데이터 |

### 페이지네이션 응답 형식

목록 조회 API는 아래 형식을 사용합니다.

```json
{
  "content": [],
  "page": 1,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

### 인증 방법

로그인 후 발급받은 `accessToken`을 모든 요청 헤더에 포함합니다.

```
Authorization: Bearer {accessToken}
```

---

## 2. 인증 (Auth)

> 인증 없이 호출 가능한 공개 API입니다.

### 2.1 회원가입

```
POST /auth/signup
```

**Request Body**

```json
{
  "email": "user@example.com",
  "password": "Password1!",
  "passwordConfirm": "Password1!",
  "name": "홍길동",
  "phone": "01012345678",
  "nickname": "별빛탐험가",
  "birthdate": "1999-01-01"
}
```

> 회원가입 후 이메일 인증이 필요합니다.

---

### 2.2 이메일 인증

```
GET /auth/verify-email?token={token}
```

> 이메일로 전송된 링크에 포함된 토큰으로 인증합니다.

---

### 2.3 로그인

```
POST /auth/login
```

**Request Body**

```json
{
  "email": "user@example.com",
  "password": "Password1!"
}
```

**Response**

```json
{
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "accessTokenExpiresAt": "2024-01-01T01:00:00Z",
    "refreshTokenExpiresAt": "2024-01-15T00:00:00Z"
  }
}
```

> `accessToken` 유효시간: **1시간**  
> `refreshToken` 유효시간: **14일**

---

### 2.4 Google 소셜 로그인

```
POST /auth/google
```

**Request Body**

```json
{
  "idToken": "Google ID Token"
}
```

**Response** - 로그인과 동일

---

### 2.5 토큰 재발급

```
POST /auth/token
```

**Request Body**

```json
{
  "refreshToken": "eyJhbGci..."
}
```

> `accessToken` 만료 시 `refreshToken`으로 재발급합니다.

---

### 2.6 비밀번호 재설정 요청

```
POST /auth/password/reset-request
```

**Request Body**

```json
{
  "email": "user@example.com",
  "name": "홍길동",
  "phone": "01012345678"
}
```

> 이름/이메일/전화번호 일치 확인 후 비밀번호 재설정 링크를 이메일로 발송합니다. 링크는 **30분** 유효합니다.

---

### 2.7 비밀번호 재설정 확인

```
POST /auth/password/reset-confirm
```

**Request Body**

```json
{
  "token": "메일로 받은 토큰",
  "newPassword": "NewPass1234!",
  "confirmNewPassword": "NewPass1234!"
}
```

> 토큰 유효성 검증 후 비밀번호를 변경합니다.

---

### 2.7 이메일로 계정 찾기

```
POST /auth/find-email
```

**Request Body**

```json
{
  "name": "홍길동",
  "phone": "01012345678"
}
```

**Response**

```json
{
  "data": {
    "emails": ["user@example.com"]
  }
}
```

---

## 2.8 FCM 토큰 관리 (로그인 필요)

```
POST   /fcm/token    # 디바이스 토큰 등록
DELETE /fcm/token    # 디바이스 토큰 삭제 (로그아웃 시 호출)
```

**Request Body**

```json
{ "token": "Firebase FCM 디바이스 토큰", "deviceType": "android" }
```

---

## 3. 유저 (User)

> 모든 API에 `Authorization` 헤더가 필요합니다.

### 3.1 내 정보 조회

```
GET /users/me
```

**Response**

```json
{
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "nickname": "별빛탐험가",
    "phone": "01012345678",
    "birthdate": "1999-01-01",
    "emailVerified": true,
    "profileImageUrl": "https://...",
    "lastLoginAt": "2024-01-01T00:00:00",
    "createdAt": "2024-01-01T00:00:00"
  }
}
```

---

### 3.2 내 정보 수정

```
PATCH /users/me
```

**Request Body** (변경할 필드만 포함)

```json
{
  "nickname": "새닉네임",
  "phone": "01099999999",
  "birthdate": "1999-06-15"
}
```

---

### 3.3 프로필 이미지 업로드

```
POST /users/me/profile-image
Content-Type: multipart/form-data
```

**Form Data**

| 키 | 타입 | 설명 |
|---|---|---|
| `image` | File | 이미지 파일 (JPEG, PNG, WEBP, GIF) |

**Response**

```json
{
  "data": {
    "profileImageUrl": "https://storage.googleapis.com/..."
  }
}
```

---

### 3.4 비밀번호 변경

```
PATCH /users/password-change
```

**Request Body**

```json
{
  "currentPassword": "OldPassword1!",
  "newPassword": "NewPassword1!",
  "confirmNewPassword": "NewPassword1!"
}
```

---

### 3.5 로그아웃

```
POST /users/logout
```

---

### 3.6 회원 탈퇴

```
DELETE /users/me
```

---

## 4. 커뮤니티 - 게시글 (Post)

> 모든 API에 `Authorization` 헤더가 필요합니다.

### 게시글 타입

| 타입 | 설명 |
|---|---|
| `FREE` | 자유 게시글 |
| `REVIEW` | 관측 후기 |
| `EDUCATION` | 교육 콘텐츠 |

---

### 4.1 게시글 생성

```
POST /community/{type}/posts
```

**Request Body**

```json
{
  "title": "제목",
  "content": "내용",
  "imageUrls": ["https://..."],
  "review": {
    "location": "서울 남산",
    "observationSiteId": 1,
    "targets": ["목성", "토성"],
    "equipment": "쌍안경 10x50",
    "observationDate": "2024-01-01",
    "score": 4
  },
  "education": {
    "difficulty": "BEGINNER",
    "tags": "입문,별자리",
    "status": "PUBLISHED",
    "targets": ["오리온자리"],
    "contentUrl": "https://..."
  }
}
```

> `review` 필드는 `type=REVIEW`일 때만, `education` 필드는 `type=EDUCATION`일 때만 사용합니다.

**Response**

```json
{
  "data": { "id": 42 }
}
```

---

### 4.2 게시글 목록 조회

```
GET /community/{type}/posts
```

**Query Parameters**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 페이지 번호 (0부터 시작) |
| `size` | 20 | 페이지 크기 |
| `sortBy` | `LATEST` | 정렬 기준 (`LATEST` / `VIEWS` / `LIKES`) |
| `keyword` | - | 검색 키워드 |
| `searchBy` | `TITLE` | 검색 대상 (`TITLE` / `CONTENT`) |

---

### 4.3 게시글 상세 조회

```
GET /community/posts/{postId}
```

**Response**

```json
{
  "data": {
    "id": 42,
    "type": "REVIEW",
    "title": "남산에서 목성 관측",
    "content": "내용...",
    "authorId": 1,
    "authorNickname": "별빛탐험가",
    "authorProfileImageUrl": "https://...",
    "images": ["https://..."],
    "review": { "location": "서울 남산", "score": 4, "targets": ["목성"] },
    "education": null,
    "viewCount": 100,
    "likeCount": 25,
    "commentCount": 5,
    "liked": false,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00"
  }
}
```

---

### 4.4 게시글 수정

```
PATCH /community/posts/{postId}
```

**Request Body** (변경할 필드만 포함)

```json
{
  "title": "수정된 제목",
  "content": "수정된 내용",
  "imageUrls": ["https://..."]
}
```

---

### 4.5 게시글 삭제

```
DELETE /community/posts/{postId}
```

---

### 4.6 좋아요 토글

```
POST /community/posts/{postId}/likes/toggle
```

**Response**

```json
{
  "data": {
    "liked": true,
    "likes": 26
  }
}
```

---

### 4.7 교육 콘텐츠 평가

```
POST /community/posts/{postId}/evaluations
```

**Request Body**

```json
{
  "score": 5,
  "pros": "설명이 친절해요",
  "cons": "사진이 부족해요"
}
```

---

### 4.8 홈 화면용 조회

```
GET /community/home/reviews      # 최신 관측 후기
GET /community/home/educations   # 최신 교육 콘텐츠
GET /community/home/free-posts   # 인기 자유게시글
```

---

## 5. 커뮤니티 - 댓글 (Comment)

> 모든 API에 `Authorization` 헤더가 필요합니다.

### 5.1 댓글 작성

```
POST /community/posts/{postId}/comments
```

**Request Body**

```json
{
  "content": "댓글 내용",
  "parentId": null
}
```

> `parentId`에 부모 댓글 ID를 넣으면 대댓글이 됩니다. (최대 1단계)

---

### 5.2 댓글 목록 조회

```
GET /community/posts/{postId}/comments?page=1&size=15
```

**Query Parameters**

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 1 | 페이지 번호 (1부터 시작) |
| `size` | 15 | 페이지 크기 |

**Response**

```json
{
  "data": {
    "content": [
      {
        "id": 1,
        "authorId": 1,
        "authorNickname": "별빛탐험가",
        "authorProfileImageUrl": "https://...",
        "content": "댓글 내용",
        "parentId": null,
        "depth": 0,
        "deleted": false,
        "likeCount": 3,
        "liked": false,
        "createdAt": "2024-01-01T00:00:00"
      }
    ]
  }
}
```

---

### 5.3 댓글 수정

```
PATCH /community/posts/{postId}/comments/{commentId}
```

**Request Body**

```json
{
  "content": "수정된 댓글"
}
```

---

### 5.4 댓글 삭제

```
DELETE /community/posts/{postId}/comments/{commentId}
```

---

### 5.5 댓글 좋아요 토글

```
POST /community/posts/{postId}/comments/{commentId}/likes-toggle
```

---

## 6. 캘린더 (Calendar)

> 모든 API에 `Authorization` 헤더가 필요합니다.

### 이벤트 상태

| 상태 | 설명 |
|---|---|
| `PLANNED` | 관측 계획 |
| `COMPLETED` | 관측 완료 |
| `CANCELED` | 계획 취소 |

---

### 6.1 이벤트 생성

```
POST /calendar/events
```

**Request Body**

```json
{
  "title": "목성 관측 계획",
  "startAt": "2024-01-15T20:00:00",
  "endAt": "2024-01-15T22:00:00",
  "observationSiteId": 1,
  "targets": ["목성", "토성"],
  "lat": 37.5665,
  "lon": 126.9780,
  "placeName": "남산",
  "memo": "쌍안경 지참",
  "status": "PLANNED",
  "imageUrls": ["https://..."]
}
```

---

### 6.2 날짜별 이벤트 조회

```
GET /calendar/events/date?date=2024-01-15
```

---

### 6.3 이벤트 상세 조회

```
GET /calendar/events/{id}
```

---

### 6.4 월별 요약 조회 (달력 뷰)

```
GET /calendar/events/month?year=2024&month=1
```

**Response**

```json
{
  "data": [
    {
      "date": "2024-01-15",
      "planned": 2,
      "completed": 1,
      "canceled": 0
    }
  ]
}
```

---

### 6.5 이벤트 수정

```
PATCH /calendar/events/{id}
```

**Request Body** (변경할 필드만 포함)

```json
{
  "title": "수정된 제목",
  "removeImageIds": [1, 2],
  "addImageUrls": ["https://..."]
}
```

---

### 6.6 이벤트 삭제

```
DELETE /calendar/events/{id}
```

---

### 6.7 관측 완료 처리

```
POST /calendar/events/{id}/complete?observedAt=2024-01-15T22:00
```

---

### 6.8 관측 횟수 조회

```
GET /calendar/events/count
```

**Response**

```json
{
  "data": {
    "observationCount": 42
  }
}
```

---

## 7. 관측지 (Observation Sites)

### 7.1 전체 관측지 목록

```
GET /observationsites?page=0&size=20&sort=name,asc
```

페이지네이션을 지원합니다. 기본값: `page=0`, `size=20`, `sort=name`.

| 파라미터 | 설명 | 기본값 |
|---|---|---|
| `page` | 페이지 번호 (0부터 시작) | `0` |
| `size` | 페이지당 항목 수 | `20` |
| `sort` | 정렬 기준 (`name`, `id` 등) | `name` |

**Response**

```json
{
  "content": [ { "id": 1, "name": "남산", "latitude": 37.55, "longitude": 126.98 } ],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20
}
```

### 7.2 관측지 검색

```
GET /observationsites/name?keyword=남산
```

### 7.3 관측지 상세

```
GET /observationsites/{id}
```

**Response**

```json
{
  "data": {
    "id": 1,
    "name": "남산 팔각정",
    "latitude": 37.5512,
    "longitude": 126.9882,
    "reviewCount": 15,
    "totalLikes": 42,
    "averageScore": 4.2
  }
}
```

---

### 7.4 즐겨찾기 관측지 (로그인 필요)

```
GET    /me/saved-sites              # 즐겨찾기 목록
POST   /me/saved-sites/toggle       # 즐겨찾기 추가/제거
DELETE /me/saved-sites/{savedSiteId} # 즐겨찾기 삭제
```

**즐겨찾기 토글 Request Body**

공식 관측지:
```json
{ "siteId": 1 }
```

직접 입력한 장소:
```json
{
  "name": "우리 동네 공원",
  "latitude": 37.1234,
  "longitude": 127.1234
}
```

---

## 8. 날씨/예보 (Weather)

> 인증 없이 호출 가능합니다.

### 8.1 위치 기반 날씨 예보

```
GET /weather/ForecastData?lat=37.5665&lon=126.9780
```

**Response 구조**

```json
{
  "data": {
    "ultraForecastResponse": [
      {
        "tmef": "202401150100",
        "t1h": "2.0",
        "reh": "60",
        "sky": "1",
        "pty": "0",
        "suitability": 85
      }
    ],
    "shortForecastResponse": [...],
    "midCombinedForecastDTO": [...]
  }
}
```

| 필드 | 설명 |
|---|---|
| `ultraForecastResponse` | 초단기 예보 (1시간 단위) |
| `shortForecastResponse` | 단기 예보 (3시간 단위) |
| `midCombinedForecastDTO` | 중기 예보 |
| `suitability` | 관측 적합도 점수 (0~100) |

---

### 8.2 날씨 예보 요약

```
GET /weather/summary?lat=37.5665&lon=126.9780
```

현재 관측 적합도, 하늘 상태, 기온, 다음 관측 적합 시각을 요약 반환합니다.

**Response**

```json
{
  "success": true,
  "data": {
    "suitability": 82,
    "sky": "맑음",
    "temperature": 14,
    "nextGoodTime": "202604062100"
  }
}
```

| 필드 | 설명 |
|---|---|
| `suitability` | 현재 관측 적합도 (0~100) |
| `sky` | 현재 하늘 상태 (맑음/구름조금/구름많음/흐림) |
| `temperature` | 현재 기온 (°C) |
| `nextGoodTime` | 적합도 70 이상인 다음 시각 (yyyyMMddHHmm), 없으면 null |

---

## 9. 별 / 천체 (Star)

> 인증 없이 호출 가능합니다. (좋아요 여부는 로그인 시에만 표시)

### 9.1 천체별 관측 후기 조회

```
GET /stars/{objectName}/reviews
```

### 9.2 천체별 교육 콘텐츠 조회

```
GET /stars/{objectName}/educations
```

> `objectName` 예시: `목성`, `토성`, `오리온자리`

---

## 에러 코드

| HTTP 상태 | 설명 |
|---|---|
| `400` | 잘못된 요청 (입력값 오류) |
| `401` | 인증 필요 또는 토큰 만료 |
| `403` | 권한 없음 |
| `404` | 리소스 없음 |
| `409` | 중복 (이미 존재하는 이메일 등) |
| `500` | 서버 오류 |

**에러 응답 예시**

```json
{
  "success": false,
  "message": "이미 사용 중인 이메일입니다.",
  "data": null
}
```

---

## 10. 알림 (Notifications)

Firebase 없이 서버 DB에 알림을 저장하는 인앱 알림 시스템입니다.  
댓글 작성 / 댓글 좋아요 이벤트 발생 시 자동으로 수신자 알림함에 저장됩니다.

> **인증 필요**: 모든 엔드포인트에 JWT Bearer 토큰이 필요합니다.

### 알림 유형 (type)

| 값 | 설명 |
|---|---|
| `NEW_COMMENT` | 내 게시글에 새 댓글이 달렸을 때 |
| `COMMENT_LIKED` | 내 댓글에 좋아요가 눌렸을 때 |
| `SYSTEM` | 시스템 공지 등 |

---

### GET /notifications

알림 목록 조회 (최신순)

**Query Parameters**

| 파라미터 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `page` | int | 1 | 페이지 번호 (1부터) |
| `size` | int | 20 | 페이지 크기 |

**응답 예시**

```json
{
  "success": true,
  "message": "성공",
  "data": [
    {
      "id": 1,
      "type": "NEW_COMMENT",
      "title": "새 댓글",
      "body": "홍길동 님이 댓글을 남겼습니다: 정말 멋진 관측지네요",
      "isRead": false,
      "createdAt": "2025-04-06T12:34:56"
    }
  ]
}
```

---

### GET /notifications/unread-count

읽지 않은 알림 수 조회

**응답 예시**

```json
{
  "success": true,
  "message": "성공",
  "data": 3
}
```

---

### PATCH /notifications/{id}/read

특정 알림 읽음 처리

**Path Parameters**

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 알림 ID |

**응답 예시**

```json
{
  "success": true,
  "message": "성공",
  "data": null
}
```

---

### PATCH /notifications/read-all

모든 알림 읽음 처리

**응답 예시**

```json
{
  "success": true,
  "message": "성공",
  "data": null
}
```
