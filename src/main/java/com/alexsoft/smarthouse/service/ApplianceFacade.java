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
import static com.alexsoft.smarthouse.util.DateUtils.*;

@Service
@RequiredArgsConstructor
public class ApplianceFacade {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceFacade.class);

    private volatile String lastKnownAcRunningState;

    private final ApplianceRepository applianceRepository;
    private final MessageSenderService messageSenderService;
    private final EventRepository eventRepository;
    private final IndicationServiceV3 indicationServiceV3;

    public void toggle(Appliance appliance, ApplianceState newState, LocalDateTime utc, String requester, boolean sendMqtt) {
        boolean switched = false;
        if (newState != appliance.getState()) {
            appliance.setState(newState, utc);
            switched = true;
        }

        if (appliance.getCode().equals("AC")) {
            indicationServiceV3.save(IndicationV3.builder().publisherId("i7-4770k").measurementType("state").localTime(toLocalDateTime(utc)).utcTime(utc)
                    .locationId("935-CORKWOOD-AC").value(appliance.getState() == ON ? 1.0 : 0.0).build());
            if (switched) {
                lastKnownAcRunningState = null;
            }
        }

        setLock(appliance, utc, requester, switched);

        if (switched) {
            LOGGER.info("Switching '{}' {}: '{}'", appliance.getCode(), newState, requester);
            String mqttTopic = requester != null && requester.startsWith("zigbee2mqtt/") ? requester : null;
            eventRepository.save(Event.builder().utcTime(utc).type("switch").device(appliance.getCode()).mqttTopic(mqttTopic)
                    .data(Map.of("state", newState.name(), "source", requester)).build());
            applianceRepository.save(appliance);
        }
        if (sendMqtt) {
            sendState(appliance);
        }
    }

    private void setLock(Appliance appliance, LocalDateTime utc, String requester, boolean switched) {

        if (switched) {
            if (appliance.getState() == OFF) {
                if (appliance.getMinimumOffCycleMinutes() != null) {
                    appliance.setLocked(true);
                    LocalDateTime lockedUntilUtc = utc.plusMinutes(appliance.getMinimumOffCycleMinutes());
                    appliance.setLockedUntilUtc(lockedUntilUtc);
                    eventRepository.save(Event.builder().utcTime(getUtc()).type("locked-until").device(appliance.getCode())
                            .data(Map.of("until", lockedUntilUtc.toString(), "rule", 5)).build());
                }
            } else {
                if (appliance.getMinimumOnCycleMinutes() != null) {
                    appliance.setLocked(true);
                    LocalDateTime lockedUntilUtc = utc.plusMinutes(appliance.getMinimumOnCycleMinutes());
                    appliance.setLockedUntilUtc(lockedUntilUtc);
                    eventRepository.save(Event.builder().utcTime(getUtc()).type("locked-until").device(appliance.getCode())
                            .data(Map.of("until", lockedUntilUtc.toString(), "rule", 6)).build());
                }
            }
        }
    }

    public void sendState(Appliance appliance) {
        if (appliance.getZigbee2MqttTopic() != null) {
            messageSenderService.sendMessage(appliance.getZigbee2MqttTopic(), "{\"state\": \"%s\"}"
                    .formatted(appliance.getState() == ON ? "on" : "off"));
        } else {
            messageSenderService.sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"%s\",\"state\":\"%s\"}"
                    .formatted(appliance.getCode(), appliance.getState() == ON ? "on" : "off"));
        }

        if (appliance.getCode().equals("AC")) {
            sendAcSetpointIfUnconfirmed(appliance);
        }
    }

    public void sendAcSetpointIfUnconfirmed(Appliance appliance) {
        if (!appliance.getCode().equals("AC")) return;
        LocalDateTime utc = getUtc();
        indicationServiceV3.save(IndicationV3.builder().publisherId("i7-4770k").measurementType("state")
                .localTime(toLocalDateTime(utc)).utcTime(utc)
                .locationId("935-CORKWOOD-AC").value(appliance.getState() == ON ? 1.0 : 0.0).build());
        if (isAcRunningStateConfirmed(appliance.getState(), lastKnownAcRunningState)) {
            LOGGER.debug("AC running_state {} confirmed, skip sending (got={})", appliance.getState(), lastKnownAcRunningState);
            return;
        }
        double coolingSetpoint = appliance.getState() == ON ? appliance.getSetting() - 3.0 : appliance.getSetting() + 3.0;
        LOGGER.warn("AC running_state mismatch detected: expected={}, got={} — resending setpoint={}", appliance.getState(), lastKnownAcRunningState, coolingSetpoint);
        messageSenderService.sendMessage("zigbee2mqtt/ac-thermostat/set",
                "{\"occupied_cooling_setpoint\": %.1f}".formatted(coolingSetpoint));
    }

    public void updateAcRunningState(String runningState) {
        this.lastKnownAcRunningState = runningState;
        LOGGER.info("ac-thermostat running_state={}", runningState);
    }

    private boolean isAcRunningStateConfirmed(ApplianceState acState, String runningState) {
        if (runningState == null) return false;
        if (acState == ON) return "cooling".equalsIgnoreCase(runningState) || "cool".equalsIgnoreCase(runningState);
        return "idle".equalsIgnoreCase(runningState);
    }

}
