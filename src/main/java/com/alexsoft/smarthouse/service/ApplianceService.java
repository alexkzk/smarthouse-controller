package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.*;
import com.alexsoft.smarthouse.db.repository.IndicationRepository;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final IndicationRepository indicationRepository;
    private final MqttService mqttService;
    private final DateUtils dateUtils;

    public void switchAppliance(Appliance appliance, LocalDateTime localDateTime) {
        Long durationInMinutes = null;

        if (appliance.getSwitched() != null) {
            durationInMinutes = Math.abs(Duration.between(appliance.getSwitched(), localDateTime).toMinutes());
        }

        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);

        mqttService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));

        saveAuxApplianceMeasurement(appliance, localDateTime);
    }

    private void saveAuxApplianceMeasurement(Appliance appliance, LocalDateTime localDateTime) {
        double value = appliance.getState() == ON ? 10.0 : 0.0;
        LocalDateTime utc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Measurement humValue = new Measurement().setValue(value);
        try {
            indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("DEHUMIDIFIER").setLocalTime(localDateTime)
                    .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue));
            indicationRepository.save(Indication.builder().aggregationPeriod(AggregationPeriod.INSTANT).receivedUtc(utc).receivedLocal(localDateTime)
                    .indicationPlace("DEHUMIDIFIER").air(Air.builder().temp(Temp.builder().celsius(value).build()).build()).build());
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }
}
