plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
}

group = "com.project"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
	kotlinOptions.jvmTarget = "21"
}


configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
   implementation("org.springframework.boot:spring-boot-starter-data-jpa")
   implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
   implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
   implementation("org.springframework.boot:spring-boot-starter-jdbc")
   implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
   implementation("org.springframework.boot:spring-boot-starter-quartz")
   implementation("org.springframework.boot:spring-boot-starter-security")
   implementation("org.springframework.boot:spring-boot-starter-web")
   implementation("org.springframework.boot:spring-boot-starter-webflux")
   implementation("org.springframework.boot:spring-boot-starter-cache")
   implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
   implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
   implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
   implementation("org.jetbrains.kotlin:kotlin-reflect")
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
   implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
   implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.7.0")
   compileOnly("org.projectlombok:lombok")
   runtimeOnly("com.mysql:mysql-connector-j")
   runtimeOnly("org.postgresql:postgresql")
   annotationProcessor("org.projectlombok:lombok")
   testImplementation("org.springframework.boot:spring-boot-starter-test")
   testImplementation("org.springframework.boot:spring-boot-testcontainers")
   testImplementation("io.projectreactor:reactor-test")
   testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
   testImplementation("org.springframework.security:spring-security-test")
   testImplementation("org.testcontainers:junit-jupiter")
   testImplementation("org.testcontainers:mongodb")
   testImplementation("org.testcontainers:mysql")
   testImplementation("org.testcontainers:postgresql")
   runtimeOnly("org.springframework.boot:spring-boot-docker-compose")
   testRuntimeOnly("org.junit.platform:junit-platform-launcher")
   implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
   implementation("io.github.cdimascio:dotenv-java:3.2.0")


   //jwt 토큰 관련 라이브러리
   implementation ("io.jsonwebtoken:jjwt-api:0.12.3")
   implementation ("io.jsonwebtoken:jjwt-impl:0.12.3")
   implementation ("io.jsonwebtoken:jjwt-jackson:0.12.3")

   testImplementation("org.mockito:mockito-core:4.11.0")
   testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}