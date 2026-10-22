# syntax=docker/dockerfile:1

### 1) Build stage
FROM gradle:8.9.0-jdk21 AS build
WORKDIR /workspace
COPY . .
# 빠른 배포를 위해 테스트 제외(원하면 -x test 제거)
RUN gradle --no-daemon clean bootJar -x test

### 2) Runtime stage
FROM eclipse-temurin:21-jre-jammy AS runtime
# 보안상 non-root
RUN useradd -ms /bin/bash appuser
WORKDIR /app
# 빌드 산출물 복사(파일명 상관없이 단일 jar로)
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

# /app/uploads 디렉토리를 만들고 소유권을 appuser 사용자에게 부여
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app/uploads
# 운영 업로드 경로(STORAGE_LOCAL_BASE_DIR=/data/uploads, named volume 마운트 지점).
# 이미지에 이 경로가 없으면 새로 만든 빈 볼륨이 root:root 로 붙어 appuser 가 업로드 폴더를 못 만든다.
# 빈 named volume 은 첫 마운트 때 이미지 디렉터리의 소유권을 복사해 가므로 여기서 미리 만들어 둔다.
RUN mkdir -p /data/uploads && chown appuser:appuser /data/uploads

EXPOSE 8080
USER appuser
ENTRYPOINT ["java","-Duser.timezone=Asia/Seoul","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]