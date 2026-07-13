FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S maple && adduser -S maple -G maple
COPY --from=build /workspace/build/libs/*.jar app.jar
USER maple
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
