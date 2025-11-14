package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class InfluxRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(InfluxRepository.class);

    @Value("${influxdb.url}")
    private String influxDbUrl;

    @Value("${influxdb.token}")
    private String influxDbToken;

    @Value("${influxdb.org}")
    private String influxDbOrg;

    @Value("${influxdb.bucket}")
    private String influxDbBucket;

    private InfluxDBClient influxDBClient;

    private final IndicationRepositoryV3 indicationRepositoryV3;

    @PersistenceContext
    private EntityManager entityManager;

    @PostConstruct
    private void initializeInfluxDbClient() {
        this.influxDBClient = InfluxDBClientFactory.create(influxDbUrl, influxDbToken.toCharArray());
    }

    public int syncAllFromPostgresBy1Days(LocalDateTime startDate, LocalDateTime endDate) {

        if (startDate == null) {
            startDate = indicationRepositoryV3.findMinUtcTime();
        }

        if (endDate == null) {
            endDate = indicationRepositoryV3.findMaxUtcTime();
        }

        int totalSynced = 0;
        LocalDateTime chunkStart = startDate;

        while (!chunkStart.isAfter(endDate)) {
            LocalDateTime chunkEnd = chunkStart.plusDays(1);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }

            LOGGER.info("Syncing indications from {} to {}", chunkStart, chunkEnd);

            List<IndicationV3> indications =
                    indicationRepositoryV3.findByUtcTimeBetween(chunkStart, chunkEnd);

            totalSynced += saveAll(indications);

            entityManager.clear();

            // move to next 1‑day window, avoid overlap
            chunkStart = chunkEnd.plusNanos(1);
        }

        return totalSynced;
    }

    public int saveAll(List<IndicationV3> indications) {

        List<Point> points = indications.stream().map(this::convertToPoint).toList();

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        try {
            writeApi.writePoints(influxDbBucket, influxDbOrg, points);
        } catch (Exception e) {
            LOGGER.error("Failed to batch write IndicationV3 to InfluxDB", e);
        }

        return points.size();

    }

    private Point convertToPoint(IndicationV3 indication) {
        Point point;

        try {
            point = Point.measurement("mqtt")
                    .addTag("host", "smarthouse-server")
                    .addTag("location_id", indication.getLocationId())
                    .addTag("publisher_id", indication.getPublisherId())
                    .addTag("mqtt_topic", indication.getMqttTopic())
                    .addField(
                            indication.getLocationId().equals("HOME-AC") ? "state" : indication.getMeasurementType(),
                            indication.getValue()
                    )
                    .time(indication.getUtcTime().atZone(java.time.ZoneOffset.UTC).toInstant(), WritePrecision.NS);
        } catch (Exception e) {
            LOGGER.error("Failed to convert IndicationV3 to Point: {}", indication, e);
            throw e; // Rethrow to handle upstream
        }

        return point;
    }

    public void saveIndicationV3ToInflux(IndicationV3 indication) {
        if (indication.getLocationId().equals("HOME-DEH")) {
            return;
        }
        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

            Point point = Point.measurement("mqtt")
                    .addTag("host", "smarthouse-server")
                    .addTag("location_id", indication.getLocationId())
                    .addTag("publisher_id", indication.getPublisherId())
                    .addTag("mqtt_topic", indication.getMqttTopic())
                    .addField(indication.getLocationId().equals("HOME-AC") ? "state" : indication.getMeasurementType(), indication.getValue())
                    .time(indication.getUtcTime().atZone(java.time.ZoneOffset.UTC).toInstant(), WritePrecision.NS);

            writeApi.writePoint(influxDbBucket, influxDbOrg, point);
        } catch (Exception e) {
            LOGGER.error("Failed to save IndicationV3 to InfluxDB: {}", indication, e);
        }
    }

}
