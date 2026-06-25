package com.alexsoft.smarthouse.appliance.listener;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.mqtt.MqttMessageReceivedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttApplianceListener {

    private final ApplianceService applianceService;
    private final ApplianceFacade applianceFacade;

    @EventListener
    public void handleMqttMessage(MqttMessageReceivedEvent event) {
        String topic = event.getTopic();
        String payload = event.getPayload();
        try {
            if (topic != null && !topic.startsWith("zigbee2mqtt/bridge") && !topic.endsWith("/set") && !topic.startsWith("mqtt.smarthouse")) {
                Map<String, Object> map = new ObjectMapper().readValue(payload, new TypeReference<>() {});
                String deviceId = topic.split("/")[1];

                Optional<Appliance> applianceByCode = applianceService.getApplianceByCode(deviceId)
                        .or(() -> applianceService.getApplianceByZigbeeTopic(topic));
                if (applianceByCode.isPresent() && map.containsKey("state")) {
                    String receivedState = (String) map.get("state");
                    Appliance appliance = applianceByCode.get();
                    Object brightnessRaw = map.get("brightness");
                    boolean isDimmingToOff = "ON".equalsIgnoreCase(receivedState)
                            && brightnessRaw != null
                            && getValue(String.valueOf(brightnessRaw)) < 30;
                    if (!isDimmingToOff && !appliance.getState().name().equalsIgnoreCase(receivedState)) {
                        applianceFacade.toggle(appliance, ApplianceState.valueOf(receivedState.toUpperCase()), getUtc(), topic, false);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing MQTT appliance message: {}", payload, e);
        }
    }

    private Double getValue(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return "ON".equalsIgnoreCase(value) ? 1.0 : 0.0;
        }
    }
}
