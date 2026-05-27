FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S gola && adduser -S gola -G gola
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
RUN chown gola:gola app.jar
USER gola
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]