FROM amazoncorretto:21 AS builder
WORKDIR /var/tmp/smarthouse
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw clean install

FROM amazoncorretto:21
ARG DEPENDENCY=/var/tmp/smarthouse/target
COPY --from=builder ${DEPENDENCY}/*.jar /opt/smarthouse/smarthouse.jar
COPY --from=builder ${DEPENDENCY}/classes/*.yml /var/smarthouse/
ENTRYPOINT ["java", "-jar", "-Dspring.config.location=/var/smarthouse/application.yml", "-Dspring.config.location=file:///var/smarthouse/", "-Dspring.profiles.active=docker", "/opt/smarthouse/smarthouse.jar"]
