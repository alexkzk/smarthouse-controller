package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.Appliance;
import com.alexsoft.smarthouse.db.entity.IndicationV2;
import com.alexsoft.smarthouse.db.entity.Measurement;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class ApplianceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceService.class);

    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final MqttSender mqttSender;

    public void switchAppliance(Appliance appliance, LocalDateTime localDateTime) {
        Long durationInMinutes = appliance.getTurnedOn() != null && appliance.getTurnedOff() != null ?
                Duration.between(appliance.getTurnedOn(), appliance.getTurnedOff()).toMinutes() : null;
        LOGGER.info("{} is {} for {} minutes", appliance.getDescription(), appliance.getFormattedState(), durationInMinutes);
        mqttSender.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        Measurement humValue = new Measurement().setValue(appliance.getState() == ON ? 10.0 : 0.0);
        try {
            indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("APT2107S-HUM").setLocalTime(localDateTime)
                    .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue));
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }
}
