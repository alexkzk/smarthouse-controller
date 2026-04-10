# Smarthouse Controller

A Spring Boot backend for home automation and IoT data collection. Runs on a local server and integrates with Zigbee2MQTT, PostgreSQL, and InfluxDB to automate smart appliances and track environmental data.

## What it does

**Appliance automation**
- Controls lights, AC, dehumidifier, and ventilation fan via MQTT/Zigbee2MQTT
- Evaluates sensor readings (illuminance, temperature, absolute humidity) against configurable thresholds with hysteresis
- Applies inverted logic for lights: turns ON when it gets dark, OFF when it gets bright
- Supports manual lock/unlock to prevent automation from overriding user-set states
- Respects minimum on/off cycle durations per appliance
- Group-level turn-off scheduling (e.g. lights off at 10 PM)
- Sunrise/sunset-aware locking: locks lights until wake-up time after dark, until sunset+1h during the day

**Sensor data collection**
- Subscribes to `zigbee2mqtt/#` and persists all measurements (temperature, humidity, illuminance, power) to PostgreSQL
- Syncs data to InfluxDB for time-series visualization
- Fetches METAR weather data for ~25 airports every 30 minutes (parallelized)
- Fetches weather forecast (WeatherAPI) and BTC/USD price on a schedule
- Tracks aircraft activity in a configurable airspace area

**Scheduled aggregation**
- Minutely, hourly, daily, and monthly aggregations over sensor data
- Trend calculation (units/hour) for temperature and humidity

**REST API**
- Query and control appliances: toggle state, set/clear locks, adjust thresholds
- All state changes and automation decisions logged as structured events in PostgreSQL

## Tech stack

- **Java 17 / Spring Boot 3.4**
- **Spring Data JPA** + PostgreSQL
- **Spring Integration MQTT** (Mosquitto broker via Zigbee2MQTT)
- **InfluxDB 2.0** (time-series storage)
- **Lombok**, **MapStruct**
- **Docker / docker-compose** for deployment

## Architecture highlights

- Two thread pools: a small scheduler pool for fast critical tasks (power control every 10s), and a separate async IO pool for all external calls and DB-heavy work — scheduler threads are never blocked by I/O
- Hysteresis-based control loop prevents appliance chatter
- Event sourcing: every automation decision, MQTT message, and lock change is persisted as a typed event with structured JSON data

## Running locally

**Prerequisites:** JDK 17+, Maven, Docker

```bash
# Build
./mvnw clean package -DskipTests

# Start infrastructure
cp .env.example .env   # fill in required values
docker-compose up -d smarthouse-db smarthouse-influxdb smarthouse-mqtt-broker

# Run
java -Dspring.profiles.active=local -jar target/*.jar
```

## Docker deployment

```bash
docker-compose up -d
```

See `.env.example` for all required environment variables (API tokens, DB credentials, etc.).

## Database operations

Dump sensor data:
```bash
docker exec -i smarthouse-db pg_dump -U smarthouse -d smarthouse \
  --table="main.indication_v3" --data-only | gzip > indication_v3.sql.gz
```

Restore:
```bash
gunzip -c indication_v3.sql.gz | psql -U smarthouse -d smarthouse -h localhost -p 24870
```

Sync PostgreSQL data to InfluxDB:
```bash
curl -X POST 'http://localhost:24867/smarthouse/indications/influx-sync'
# Optional date range:
curl -X POST 'http://localhost:24867/smarthouse/indications/influx-sync?startDate=2025-10-17T00:00:00&endDate=2025-10-27T00:00:00'
```
