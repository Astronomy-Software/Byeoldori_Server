# 🔭 Byeoldori Server (별도리 서버)

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/SpringBoot-3.4.3-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Cache-DC382D?style=for-the-badge&logo=redis&logoColor=white)

## 📖 Project Overview
**Byeoldori(별도리)**는 사용자가 체계적이고 신뢰할 수 있는 천문학 정보를 쉽게 접할 수 있도록 설계되었으며, 마스코트 캐릭터가 진행하는 학습과 관측을 결합한 흥미로운 프로그램을 통해 천체 관측 경험이 없는 사용자들이 관측에 대한 두려움을 극복하고 천문학에 쉽게 입문할 수 있도록 돕는 것이 주요 목적입니다.

본 리포지토리는 Byeoldori 서비스의 **Backend Server**로, 안정적인 데이터 처리와 정확한 정보 제공을 목표로 합니다.

### 🌟 Key Features
- **🔑 인증/인가**: JWT(Access/Refresh) 기반 인증, Google OAuth2 소셜 로그인, 이메일 인증
- **☁️ 기상 예보**: 기상청 API 연동, WebFlux 기반 비동기 데이터 처리, Quartz Scheduler를 이용한 주기적 예보 업데이트
- **🌙 천문 계산**: Apache Commons Math3를 활용한 달의 위상, 고도 및 관측 적합도 알고리즘 구현
- **👥 커뮤니티**: 게시글 작성, 댓글, 좋아요, 이미지 업로드(Local/S3 확장 가능)
- **📅 캘린더**: 개인별 관측 일정 관리 및 사진 기록

---

## 🛠 Tech Stack

| Category | Technology |
| --- | --- |
| **Language** | Kotlin (JDK 21) |
| **Framework** | Spring Boot 3.4.3 |
| **Database** | MySQL (RDBMS), Redis (Cache) |
| **ORM / Data** | Spring Data JPA, QueryDSL |
| **Async / Reactive** | Spring WebFlux, Project Reactor, Quartz Scheduler |
| **API Docs** | SpringDoc OpenAPI (Swagger) |
| **Testing** | JUnit 5, Mockito, Testcontainers |

---

## 🚀 Getting Started

로컬 개발 환경에서 프로젝트를 실행하는 방법입니다.

### Prerequisites
* **Java 21** 이상
* **MySQL** (Port 3306)
* **MongoDB** (Port 27017)
* **Redis** (선택 사항, 미구동 시 관련 기능 제한될 수 있음)

### 1. Environment Setup (`application.yml` or Environment Variables)
프로젝트 실행을 위해 환경 변수 설정이 필요합니다. IntelliJ의 'Edit Configurations' 또는 OS 환경 변수로 등록하세요.

| 변수명 | 설명 | 예시 값 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | MySQL 접속 주소 | `jdbc:mysql://localhost:3306/byeoldori` |
| `SPRING_DATASOURCE_USERNAME` | MySQL 계정 | `root` |
| `SPRING_DATASOURCE_PASSWORD` | MySQL 비밀번호 | `password` |
| `SPRING_DATA_MONGODB_HOST` | MongoDB 호스트 | `localhost` |
| `WEATHER_API_KEY` | 기상청 API 인증키 | `Decoding된_공공데이터_키` |
| `WEATHER_API_URL` | 기상청 API URL | `http://apis.data.go.kr/...` |
| `JWT_SECRET` | JWT 서명용 비밀키 | `임의의_긴_문자열(Base64)` |
| `GOOGLE_CLIENT_ID` | 구글 OAuth 클라이언트 ID | `...apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | 구글 OAuth 클라이언트 시크릿 | `...` |
| `MAIL_USERNAME` | SMTP 발송 이메일 | `example@gmail.com` |
| `MAIL_PASSWORD` | SMTP 앱 비밀번호 | `abcd 1234 ...` |
