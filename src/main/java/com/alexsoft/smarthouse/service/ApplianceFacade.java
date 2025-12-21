package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.repository.ApplianceRepository;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;
import static com.alexsoft.smarthouse.service.ApplianceService.MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC;
import static com.alexsoft.smarthouse.utils.DateUtils.*;

@Service
@RequiredArgsConstructor
public class ApplianceFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceFacade.class);


    private final ApplianceRepository applianceRepository;
    private final MessageSenderService messageSenderService;
    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final EventRepository eventRepository;

    public void toggle(Appliance appliance, ApplianceState newState, LocalDateTime utc, String requester, boolean sendMqtt) {
        boolean switched = false;
        if (newState == ON && appliance.getState() == OFF) {
            appliance.setState(newState, utc);
            switched = true;
        } else if (newState == OFF && appliance.getState() == ON) {
            appliance.setState(OFF, utc);
            switched = true;
        }
        setLock(appliance, utc, requester, switched);

        if (switched) {
            LOGGER.info("Switching '{}' {}: '{}'", appliance.getCode(), newState, requester);
            eventRepository.save(Event.builder().utcTime(utc).type("switch.%s.%s".formatted(appliance.getCode(), newState)).build());
            applianceRepository.save(appliance);
        }
        if (sendMqtt) {
            sendState(appliance);
        }
    }

    private static void setLock(Appliance appliance, LocalDateTime utc, String requester, boolean switched) {

        if (("http-controller".equals(requester) || "mqtt-msg".equals(requester)) &&
                appliance.getApplianceGroup().filter(gr -> gr.getId() == 1).isPresent()) {
            if (isDark()) {
                if (appliance.getState() == OFF) {
                    appliance.setLocked(true);
                    appliance.setLockedUntilUtc(sixThirtyAmAtUtc());
                } else {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                }
            } else {
                if (appliance.getState() == OFF) {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                } else {
                    appliance.setLocked(true);
                    appliance.setLockedUntilUtc(toUtc(getNearestSunsetTime().plusHours(1)));
                }
            }
        } else if (switched) {
            if (appliance.getState() == OFF) {
                if (appliance.getMinimumOffCycleMinutes() != null) {
                    appliance.setLocked(true);
                    appliance.setLockedUntilUtc(utc.plusMinutes(appliance.getMinimumOffCycleMinutes()));
                }
            } else {
                if (appliance.getMinimumOnCycleMinutes() != null) {
                    appliance.setLocked(true);
                    appliance.setLockedUntilUtc(utc.plusMinutes(appliance.getMinimumOnCycleMinutes()));
                }
            }
        }
    }

    private void sendState(Appliance appliance) {
        LocalDateTime utc = getUtc();
        if (appliance.getZigbee2MqttTopic() != null) {
            if (appliance.getCode().equals("LR-LUTV") && (toLocalDateTime(utc).getHour() < 7 || toLocalDateTime(utc).getHour() > 21)) {
                messageSenderService.sendMessage(appliance.getZigbee2MqttTopic(), "{\"state\": \"%s\", \"brightness\":%d}"
                        .formatted("on", appliance.getState() == ON ? 160 : 20));
            } else {
                String brightness;
                if (List.of("MB-LOTV", "MB-LOB", "LR-LUTV").contains(appliance.getCode())) {
                    if (appliance.getPowerSetting() == null) {
                        brightness = ", \"brightness\": 160";
                    } else {
                        brightness = ", \"brightness\": %d".formatted((int) (255 * (appliance.getPowerSetting() / 100)));
                    }
                } else {
                    brightness = "";
                }
                messageSenderService.sendMessage(appliance.getZigbee2MqttTopic(), ("{\"state\": \"%s\"" + brightness + "}")
                        .formatted(appliance.getState() == ON ? "on" : "off"));
                if (appliance.getCode().equals("TER-LIGHTS")) {
                    messageSenderService.sendMessage("zigbee2mqtt/WRKTABLE/set", ("{\"state\": \"%s\"" + brightness + "}")
                            .formatted(appliance.getState() == ON ? "on" : "off"));
                }
            }
            //  Turn on the FAN periodically while DEH is on
            if (appliance.getCode().equals("DEH") && applianceRepository.findById("AC").get().getState() == OFF) {
                boolean fanNeedsToBeTurnedOn;
                LOGGER.info("mqtt.smarthouse.power.control inside");
                if (toLocalDateTime(utc).getHour() > 22 || toLocalDateTime(utc).getHour() < 8) {
                    // night time
                    fanNeedsToBeTurnedOn = List.of(26, 27, 28, 29, 56, 57, 58, 59)
                            .contains(toLocalDateTime(utc).getMinute());
                } else {
                    // day time
                    fanNeedsToBeTurnedOn = List.of(17, 18, 19, 37, 38, 39, 57, 58, 59)
                            .contains(toLocalDateTime(utc).getMinute());
                }

                OptionalDouble avgDehPowerConsumption = indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(List.of("lr-sp-dehumidifier"),
                        utc.minus(Duration.ofMinutes(appliance.getAveragePeriodMinutes())), "power").stream().mapToDouble(IndicationV3::getValue).average();

                if (appliance.getState() == ON && avgDehPowerConsumption.isPresent() && avgDehPowerConsumption.getAsDouble() > 500) {
                    if (fanNeedsToBeTurnedOn) {
                        messageSenderService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}".formatted("FAN", "on"));
                        indicationRepositoryV3.save(IndicationV3.builder().publisherId("smarthouse-server").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                                .locationId("HOME-FAN").value(1.0).build());
                    } else {
                        messageSenderService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}".formatted("FAN", "off"));
                        indicationRepositoryV3.save(IndicationV3.builder().publisherId("smarthouse-server").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                                .locationId("HOME-FAN").value(0.0).build());
                    }
                } else {
                    messageSenderService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}".formatted("FAN", "off"));
                    indicationRepositoryV3.save(IndicationV3.builder().publisherId("smarthouse-server").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                            .locationId("HOME-FAN").value(0.0).build());

                }
            }
        } else {
            messageSenderService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                    .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        }

        if (appliance.getCode().equals("AC")) {
            indicationRepositoryV3.save(IndicationV3.builder().publisherId("smarthouse-server").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                    .locationId("HOME-AC").value((double) (appliance.getState() == ON ? 1 : 0)).build());
        }
    }

}
