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

---

## 🗄 운영 데이터 백업과 복원

운영 VM 에서는 `scripts/backup.sh` 가 매일 03:30(KST) cron 으로 돈다. cron 항목은 배포 때 `scripts/install-backup-cron.sh` 가 설치·갱신한다.

- 대상: MySQL `byeoldori` 전체 덤프, MongoDB 전체(`mongodump --archive --gzip`), 업로드 파일(`uploads` 볼륨)
- 위치: `~/byeoldori-backups/<YYYYMMDD-HHMMSS>/` (기본 7일 보관), 실행 로그는 `~/byeoldori-backups/backup.log`
- 실패하면 Discord(Grafana 알림과 같은 웹훅)로 알린다.
- 로컬 백업은 같은 부트볼륨에 있어서 VM 을 통째로 잃으면 함께 사라진다. VM 밖 보관은 `~/.byeoldori-backup.env` 에 `BACKUP_OCI_BUCKET=<버킷>` 을 두고 VM 에 oci CLI 를 설정하면 켜진다(버킷 수명주기 규칙으로 보존 기간 관리). OCI 콘솔의 부트볼륨 백업 정책도 함께 켜 둔다.

복원(VM 에서, 대상 폴더로 이동한 뒤):

```bash
sha256sum -c SHA256SUMS
# MySQL — 덤프에 CREATE DATABASE/USE 가 들어 있어 byeoldori 를 통째로 다시 만든다
gunzip -c mysql-byeoldori.sql.gz | docker exec -i mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'
# MongoDB — --drop 은 백업에 있는 컬렉션을 지우고 다시 넣는다
docker exec -i mongodb sh -c 'exec mongorestore --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive --gzip --drop' < mongo.archive.gz
# 업로드 파일
VOL=$(docker volume ls -q --filter label=com.docker.compose.volume=uploads | head -1)
docker run --rm -i --user 0 --entrypoint tar -v "$VOL":/data/uploads byeoldori-server:local -xzf - -C /data/uploads < uploads.tar.gz
```
