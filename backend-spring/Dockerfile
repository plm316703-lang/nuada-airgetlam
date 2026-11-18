# ==========================================================
# Spring 관문 서버 이미지
# 두 단계(멀티 스테이지)로 나눈다:
#   1단계(build)  : JDK로 bootJar를 만든다 → 무겁지만 빌드에만 쓰고 버림
#   2단계(실행)   : JRE만 있는 가벼운 이미지에 jar 하나만 옮겨 담는다
# ==========================================================

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# (1) Gradle 설정 파일만 먼저 복사 → 의존성 다운로드 결과를 캐시로 재사용
#     소스만 바뀌었을 때는 라이브러리를 다시 받지 않아 빌드가 빨라진다
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# Windows에서 받은 파일이면 줄바꿈(CRLF)이 섞일 수 있어 LF로 정리하고 실행 권한을 준다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon > /dev/null

# (2) 소스 복사 후 실행 가능한 jar 만들기
#     -x test : 테스트는 실제 MySQL이 필요해서 이미지 빌드 중에는 건너뛴다
#               (테스트는 IntelliJ에서 로컬 DB로 따로 돌린다)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 보안: root가 아닌 일반 사용자로 서버를 실행한다
RUN useradd --system --create-home spring
USER spring

# 1단계에서 만든 jar만 가져온다 (-plain.jar 는 단독 실행이 안 되므로 제외)
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
