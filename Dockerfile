# Multi-stage build: build the React UI, compile with the full JDK, run on a slim JRE as a non-root user.
FROM node:24-alpine AS web
WORKDIR /web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Spring Boot serves anything in static/ at the site root, so the UI and API share one origin.
COPY --from=web /web/dist src/main/resources/static
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 paycore
WORKDIR /app
COPY --from=build /src/target/paycore-0.0.1-SNAPSHOT.jar app.jar
USER paycore
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
