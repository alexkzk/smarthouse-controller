# Smart House application
### General description

The application's goal is to collect data from different home IoT sensors and other needed sources, save them into a Relational Database
(PostgreSQL) and then create different reports, charts and other statistics on the user's demand.

The application performs next actions:
- Receives MQTT messages from MQTT broker and persists them in the DB.
- On scheduled basis, loads realtime world weather data for specified locations (using AVWX REST API - https://avwx.rest/)
  and persists them in the DB (as if it were send via MQTT).
- Generates different reports (simple HTML Thymeleaf templates with Google Charts) using aggregated (minutely, hourly...) measurements
  based on previously saved data.

### Packages content
- configuration - Spring beans configuration.
- db - JPA entities and repositories.
- dto - data transfer object models.
- rest - REST controllers.
- presentation - Thymeleaf controllers.
- model - model classes used in the business logic.
- scheduled - scheduled events invoked using Spring cron
- service - business logic related classes.
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

## Redeployment steps
-- From dev machine

`sudo docker build -t alexsoft/smarthouse ~/IdeaProjects/smarthouse-controller && sudo docker tag alexsoft/smarthouse smarthouse:1.37 && sudo docker push smarthouse:1.37`

-- From server

`sudo docker stop smarthouse && sudo docker rm smarthouse && sudo docker run --restart=always -d -p 8080:8080 --name=smarthouse smarthouse:1.05 && sudo docker logs -f smarthouse`

Author: Author. 2020.