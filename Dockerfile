FROM openjdk:11-jdk-slim as build
WORKDIR /var/tmp/smarthouse
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw clean install

FROM arm64v8/adoptopenjdk:11-jdk-hotspot-focal
ARG DEPENDENCY=/var/tmp/smarthouse/target
COPY --from=build ${DEPENDENCY}/*.jar /opt/smarthouse/smarthouse.jar
COPY --from=build ${DEPENDENCY}/classes/*.yml /var/smarthouse/
ENTRYPOINT ["java","-jar", "-Dspring.config.location=/var/smarthouse/application.yml", "-Dspring.config.location=file:///var/smarthouse/", "-Dspring.profiles.active=docker", "/opt/smarthouse/smarthouse.jar"]
