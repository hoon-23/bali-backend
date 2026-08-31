# 1단계: 전체 소스로 bali-api만 빌드 (멀티모듈이라 루트 컨텍스트 필요)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :bali-api:bootJar --no-daemon -x test

# 2단계: 실행 jar만 담은 최소 런타임 이미지
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bali-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
