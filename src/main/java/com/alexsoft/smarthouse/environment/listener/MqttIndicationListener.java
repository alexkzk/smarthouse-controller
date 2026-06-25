package com.alexsoft.smarthouse.environment.listener;

import com.alexsoft.smarthouse.environment.IndicationServiceV3;
import com.alexsoft.smarthouse.environment.IndicationV3;
import com.alexsoft.smarthouse.environment.IndicationV3.IndicationV3Builder;
import com.alexsoft.smarthouse.mqtt.MqttMessageReceivedEvent;
import com.alexsoft.smarthouse.core.util.TempUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alexsoft.smarthouse.core.util.DateUtils.getLocalDateTime;
import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;

@Component
@RequiredArgsConstructor
@Slf4j
public class MqttIndicationListener {

    private final IndicationServiceV3 indicationServiceV3;
    private final TempUtils tempUtils = new TempUtils();
    private static final List<String> MEASUREMENT_TYPES = List.of("power", "voltage", "current");
    private static final Map<String, String> UNITS_MAP = Map.of("power", "W", "voltage", "V", "current", "A");

    @EventListener
    public void handleMqttMessage(MqttMessageReceivedEvent event) {
        String topic = event.getTopic();
        String payload = event.getPayload();
        try {
            if (topic != null && topic.startsWith("mqtt.smarthouse") && payload.contains("celsius")) {
                Map<String, Object> map = new ObjectMapper().readValue(payload, new TypeReference<>() {});
                String publisherId = (String) map.get("publisherId");
                String locationId = (String) map.get("measurePlace");

                if (publisherId != null && locationId != null) {
                    Map<String, Object> air = (Map<String, Object>) map.get("air");
                    Map<String, Object> temp = air != null ? (Map<String, Object>) air.get("temp") : null;
                    Map<String, Object> pressure = air != null ? (Map<String, Object>) air.get("pressure") : null;

                    IndicationV3Builder builder = IndicationV3.builder()
                            .localTime(getLocalDateTime())
                            .utcTime(getUtc())
                            .publisherId(publisherId)
                            .locationId(locationId)
                            .mqttTopic(topic);

                    List<IndicationV3> indicationV3s = new ArrayList<>();
                    if (temp != null) {
                        Number celsius = (Number) temp.get("celsius");
                        Number rh = (Number) temp.get("rh");
                        Number ah = (Number) temp.get("ah");
                        boolean isBtc = "btc".equalsIgnoreCase(locationId);
                        if (celsius != null) {
                            indicationV3s.add(builder.measurementType(isBtc ? "money" : "temp").unit(isBtc ? "usd" : "c")
                                    .value(celsius.doubleValue()).build());
                        }
                        if (rh != null) {
                            indicationV3s.add(builder.measurementType("rh").unit("%").value(rh.doubleValue()).build());
                        }
                        Double ahValue = ah != null ? ah.doubleValue() : null;
                        if (ahValue == null && celsius != null && rh != null) {
                            ahValue = tempUtils.calculateAbsoluteHumidity(celsius.floatValue(), rh.intValue()).doubleValue();
                        }
                        if (ahValue != null) {
                            indicationV3s.add(builder.measurementType("ah").unit("g/m3").value(ahValue).build());
                        }
                    }
                    if (pressure != null) {
                        Number mmHg = (Number) pressure.get("mmHg");
                        if (mmHg != null) {
                            indicationV3s.add(builder.measurementType("pressure").unit("mmHg").value(mmHg.doubleValue()).build());
                        }
                    }
                    indicationServiceV3.saveAll(indicationV3s);
                }
            } else if (topic != null && !topic.startsWith("zigbee2mqtt/bridge") && !topic.endsWith("/set")) {
                List<IndicationV3> indicationV3s = new ArrayList<>();
                Map<String, Object> map = new ObjectMapper().readValue(payload, new TypeReference<>() {});
                String deviceId = topic.split("/")[1];
                IndicationV3Builder indicationV3Builder = IndicationV3.builder().mqttTopic(topic).localTime(getLocalDateTime())
                        .utcTime(getUtc()).publisherId("zigbee2mqtt").locationId(deviceId);

                if (map.containsKey("power")) {
                    MEASUREMENT_TYPES.forEach(m -> indicationV3s.add(indicationV3Builder.measurementType(m).unit(UNITS_MAP.get(m))
                            .value(getValue(String.valueOf(map.get(m)))).build()));
                }
                if (map.containsKey("illuminance")) {
                    indicationV3s.add(indicationV3Builder.measurementType("illuminance").unit("lux")
                            .value(getValue(String.valueOf(map.get("illuminance")))).build());
                }
                if (map.containsKey("vibration")) {
                    Object raw = map.get("vibration");
                    double vibrationValue = (raw instanceof Boolean) ? (((Boolean) raw) ? 1.0 : 0.0) : getValue(String.valueOf(raw));
                    indicationV3s.add(indicationV3Builder.measurementType("vibration").unit("bool").value(vibrationValue).build());
                }
                if (map.containsKey("occupancy")) {
                    Object raw = map.get("occupancy");
                    double occupancyValue = (raw instanceof Boolean) ? (((Boolean) raw) ? 1.0 : 0.0) : getValue(String.valueOf(raw));
                    indicationV3s.add(indicationV3Builder.measurementType("occupancy").unit("bool").value(occupancyValue).build());
                }
                if (map.containsKey("motion")) {
                    Object raw = map.get("motion");
                    double motionValue = (raw instanceof Boolean) ? (((Boolean) raw) ? 1.0 : 0.0) : getValue(String.valueOf(raw));
                    indicationV3s.add(indicationV3Builder.measurementType("motion").unit("bool").value(motionValue).build());
                }
                if (map.containsKey("presence")) {
                    Object raw = map.get("presence");
                    double presenceValue = (raw instanceof Boolean) ? (((Boolean) raw) ? 1.0 : 0.0) : getValue(String.valueOf(raw));
                    indicationV3s.add(indicationV3Builder.measurementType("motion").unit("bool").value(presenceValue).build());
                }
                if (map.containsKey("temperature")) {
                    Double temperature = getValue(String.valueOf(map.get("temperature")));
                    indicationV3s.add(indicationV3Builder.measurementType("temp").unit("c")
                            .value(temperature).build());
                }
                if (map.containsKey("local_temperature")) {
                    Double temperature = getValue(String.valueOf(map.get("local_temperature")));
                    indicationV3s.add(indicationV3Builder.locationId("935-CORKWOOD-HW").measurementType("temp").unit("c")
                            .value(temperature).build());
                    indicationV3Builder.locationId(deviceId);
                }
                if (map.containsKey("humidity")) {
                    Double rh = getValue(String.valueOf(map.get("humidity")));
                    indicationV3s.add(indicationV3Builder.measurementType("rh").unit("%")
                            .value(rh).build());
                    Double temperature = getValue(String.valueOf(map.get("temperature")));
                    if (temperature != null && rh != null) {
                        indicationV3s.add(indicationV3Builder.measurementType("ah").unit("g/m3")
                                .value(tempUtils.calculateAbsoluteHumidity(temperature.floatValue(), rh.intValue()).doubleValue()).build());
                    }
                }

                indicationServiceV3.saveAll(indicationV3s);
            }
        } catch (Exception e) {
            log.error("Error processing MQTT indication: {}", payload, e);
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
