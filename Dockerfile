FROM arm64v8/openjdk:17.0.1-jdk-slim as build
#FROM openjdk:17.0.1-jdk-slim AS builder
WORKDIR /var/tmp/smarthouse
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw clean install

# Runtime stage
FROM arm64v8/openjdk:17.0.1-jdk-slim
#FROM openjdk:17.0.1-jdk-slim
ARG DEPENDENCY=/var/tmp/smarthouse/target
#COPY --from=build /var/tmp/smarthouse/utils/pg_dump /usr/bin/pg_dump
COPY --from=builder ${DEPENDENCY}/*.jar /opt/smarthouse/smarthouse.jar
COPY --from=builder ${DEPENDENCY}/classes/*.yml /var/smarthouse/
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=/var/smarthouse/application.yml", "-Dspring.config.location=file:///var/smarthouse/", "-Dspring.profiles.active=docker", "/opt/smarthouse/smarthouse.jar"]
