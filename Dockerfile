# 1단계: 빌드 단계
FROM openjdk:21-jdk-slim as build

WORKDIR /app

# Gradle Wrapper 및 관련 파일 복사
COPY gradlew /app/gradlew
COPY gradle /app/gradle
COPY build.gradle.kts /app/build.gradle.kts
COPY settings.gradle.kts /app/settings.gradle.kts
COPY src /app/src

# Gradle Wrapper로 빌드 실행
RUN chmod +x gradlew && ./gradlew clean build -x test

# 2단계: 실행 단계
FROM openjdk:21-jdk-slim

WORKDIR /app

# 빌드한 JAR 파일을 실행 단계로 복사
COPY --from=build /app/build/libs/byeoldori-0.0.1-SNAPSHOT.jar /app/byeoldori.jar

# docker-compose.yml을 컨테이너 내부로 복사
COPY compose.yml /app/docker-compose.yml

# 포트 노출
EXPOSE 8080

RUN chmod +x /app/byeoldori.jar

# 애플리케이션 실행 명령어
CMD ["java", "-jar", "/app/byeoldori.jar"]

# Dockerfile에서 환경 변수 설정
ENV WEATHER_API_KEY=${WEATHER_API_KEY}
ENV WEATHER_API_URL=${WEATHER_API_URL}
