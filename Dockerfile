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
EXPOSE 8080
USER appuser
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]