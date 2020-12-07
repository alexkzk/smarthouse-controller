# Smart House application
Author: Alexey Kuzko aleks.ce267@gmail.com Chernivtsi, Ukraine. 2020.
### General description

The main goal of this application is to collect data from different home IoT sensors, save them into a Relational Database
(PostgreSQL is used). Application is deployed on a VPS server.

The application performs two actions: 
- Receiving MQTT messages from MQTT broker and saving them into DB.
- Loading and attaching realtime world weather data (using AVWX REST API - https://avwx.rest/) to entities before saving.
- Serving simple HTML templates (Thymeleaf is used) with aggregated (minutely, hourly...) measurements.

### Packages content
- configuration - Spring beans configuration.
- controller - REST and Thymeleaf template models controller.
- db - JPA entities and repositories.
- dto - data transfer object models.
- model:
    - messaging - MQTT message model classes.
    - metar - AVWX REST API weather domain model.
- service - business layer logic related classes.
- utils - static util classes, constants etc.

### Frameworks and tools used
- Spring Boot (embedded web-server)
- Spring Data JPA (simple storing the data into DB)
- PostgreSQL (relational database)
- Lombok (code auto-generation tool)
- Mapstruct (code auto-generation tool)
- Maven (building the application, managing dependencies)
- Liquibase (tracking, managing and applying database schema changes), temporary disabled until the database schema is finalized.

### Building and running the app
You'll need JDK and Maven installed for next steps

`mvn -f <path to pom.xml> clean install`

`java -jar <path to JAR file previously built>`