FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app:app
WORKDIR /app

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8082 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
