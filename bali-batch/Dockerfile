# 1단계: 전체 소스로 bali-batch만 빌드 (멀티모듈이라 루트 컨텍스트 필요)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :bali-batch:bootJar --no-daemon -x test

# 2단계: 실행 jar만 담은 최소 런타임 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bali-batch/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
