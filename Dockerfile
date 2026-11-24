# Multi-stage build: compile with the full JDK, run on a slim JRE as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 paycore
WORKDIR /app
COPY --from=build /src/target/paycore-0.0.1-SNAPSHOT.jar app.jar
USER paycore
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/app.jar"]
