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
- scheduled - scheduled tasks.
- service - business logic related classes.
- utils - static util classes, constants etc.

### Frameworks and tools used
- Spring Boot
- Spring Data JPA
- PostgreSQL
- Lombok
- Mapstruct
- Maven
- Liquibase

### Building and running the app
You'll need JDK and Maven installed for next steps

`mvn -f <path to pom.xml> clean install`

`java -jar <path to JAR file previously built>`

### Docker deployment
Built and push a docker image

`docker build -t alexsoft/smarthouse ~/IdeaProjects/smarthouse-controller && docker tag alexsoft/smarthouse smarthouse:1.37 && docker push smarthouse:1.37`

Run docker image

`docker stop smarthouse-controller && docker rm smarthouse-controller && docker run --restart=always -d -p 8080:8080 --name=smarthouse-controller smarthouse:1.05 && docker logs -f smarthouse-controller`

Rebuild and redeploy on PI4
`cd ~/smarthouse-controller && git pull origin master && docker-compose down && docker rmi smarthouse-controller && docker-compose up -d`


### Database operations

Dump table
`docker exec -i smarthouse-db pg_dump -U smarthouse -d smarthouse --table="main.indication_v3" --data-only | gzip  > indication_v3.sql.gz`

-- no compression
`docker exec -i smarthouse-db pg_dump -U smarthouse -d smarthouse --table="main.indication_v3" --data-only > /home/indication_v3.sql`

Copy and Gzip file from inside a container
`docker cp smarthouse-db:/home/indication_v2.sql.gz ~`

Restore locally
`SELECT setval('main.indication_sq_v3', 19449626);`
`gunzip -c ~/Downloads/indication_v3.sql.gz | psql -U smarthouse -d smarthouse -h localhost -p 24870`

Influx sync from IndicationV3 table
`influx delete   --bucket smarthouse-bucket   --org Home   --start 1970-01-01T00:00:00Z   --stop 2100-01-01T00:00:00Z`
`curl --location --request POST 'http://localhost:24867/smarthouse/indications/influx-sync?startDate=2025-10-17T00%3A00%3A00&endDate=2025-10-27T00%3A00%3A00'`


Reprocess MBS
`delete from indication_v3 where location_id = 'mbs';`
`delete   --bucket smarthouse-bucket   --org Home -p location_id=mbs   --start 1970-01-01T00:00:00Z   --stop 2100-01-01T00:00:00Z`
`curl --location --request POST 'http://localhost:24867/smarthouse/indications/mbs/reprocess'`

Author: Author. 2020.
