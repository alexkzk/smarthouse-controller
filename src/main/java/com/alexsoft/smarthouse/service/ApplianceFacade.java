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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final IndicationServiceV3 indicationServiceV3;

    public void toggle(Appliance appliance, ApplianceState newState, LocalDateTime utc, String requester, boolean sendMqtt) {
        boolean switched = false;
        if (newState != appliance.getState()) {
            appliance.setState(newState, utc);
            switched = true;
        }

        if (appliance.getCode().equals("AC")) {
            indicationServiceV3.save(IndicationV3.builder().publisherId("smarthouse-server").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                    .locationId("HOME-AC").value(appliance.getState() == ON ? 1.0 : 0.0).build());
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

    private void setLock(Appliance appliance, LocalDateTime utc, String requester, boolean switched) {

        if (("http-controller".equals(requester) || "mqtt-msg".equals(requester)) &&
                appliance.getApplianceGroup().filter(gr -> gr.getId() == 1).isPresent()) {
            if (isDark()) {
                if (appliance.getState() == OFF) {
                    appliance.setLocked(true);
                    LocalDateTime sixThirtyAm = wakeUpTime();
                    // only update if not already locked until a later time (preserve user extensions)
                    if (appliance.getLockedUntilUtc() == null || appliance.getLockedUntilUtc().isBefore(sixThirtyAm)) {
                        appliance.setLockedUntilUtc(sixThirtyAm);
                        eventRepository.save(Event.builder().utcTime(utc).type("%s.locked-until.%s#1".formatted(appliance.getCode(), sixThirtyAm)).build());
                    } else {
                        eventRepository.save(Event.builder().utcTime(utc)
                                .type("%s.lock.preserved".formatted(appliance.getCode()))
                                .data(Map.of("existing", appliance.getLockedUntilUtc().toString(), "attempted", sixThirtyAm.toString())).build());
                    }
                } else {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                    eventRepository.save(Event.builder().utcTime(utc).type("%s.unlocked#2".formatted(appliance.getCode())).build());
                }
            } else {
                if (appliance.getState() == OFF) {
                    appliance.setLocked(false);
                    appliance.setLockedUntilUtc(null);
                    eventRepository.save(Event.builder().utcTime(utc).type("%s.unlocked#4".formatted(appliance.getCode())).build());
                } else {
                    appliance.setLocked(true);
                    LocalDateTime lockedUntilUtc = toUtc(getNearestSunsetTime().plusHours(1));
                    appliance.setLockedUntilUtc(lockedUntilUtc);
                    eventRepository.save(Event.builder().utcTime(utc).type("%s.locked-until.%s#4".formatted(appliance.getCode(), lockedUntilUtc)).build());
                }
            }
        } else if (switched) {
            if (appliance.getState() == OFF) {
                if (appliance.getMinimumOffCycleMinutes() != null) {
                    appliance.setLocked(true);
                    LocalDateTime lockedUntilUtc = utc.plusMinutes(appliance.getMinimumOffCycleMinutes());
                    appliance.setLockedUntilUtc(lockedUntilUtc);
                    eventRepository.save(Event.builder().utcTime(utc).type("%s.locked-until.%s#5".formatted(appliance.getCode(), lockedUntilUtc)).build());
                }
            } else {
                if (appliance.getMinimumOnCycleMinutes() != null) {
                    appliance.setLocked(true);
                    LocalDateTime lockedUntilUtc = utc.plusMinutes(appliance.getMinimumOnCycleMinutes());
                    appliance.setLockedUntilUtc(lockedUntilUtc);
                    eventRepository.save(Event.builder().utcTime(utc).type("%s.locked-until.%s#6".formatted(appliance.getCode(), lockedUntilUtc)).build());
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
