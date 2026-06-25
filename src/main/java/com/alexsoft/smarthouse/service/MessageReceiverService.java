package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.IndicationV3.IndicationV3Builder;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.util.TempUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.time.LocalDateTime;
import java.util.*;

import static com.alexsoft.smarthouse.util.DateUtils.*;

@Configuration
@RequiredArgsConstructor
public class MessageReceiverService {

    public static final List<String> MEASUREMENT_TYPES = List.of("energy", "power", "state", "voltage");
    public static final Map<String, String> UNITS_MAP = Map.of("energy", "kWh", "power", "W", "voltage", "V", "illuminance", "lux");

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReceiverService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TempUtils tempUtils = new TempUtils();
    private final ApplianceService applianceService;
    private final IndicationServiceV3 indicationServiceV3;
    private final EventRepository eventRepository;

    @Value("tcp://${mqtt.server-in}:${mqtt.port}")
    private String mqttUrlIn;

    @Value("tcp://${mqtt.server-out}:${mqtt.port}")
    private String mqttUrlOut;

    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

    @Value("${mqtt.topic}")
    private String measurementTopic;

    @Bean
    public DefaultMqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(mqttUrlIn, mqttSubscriber + "-" + UUID.randomUUID(), new String[]{mqttTopic, "zigbee2mqtt/#"});
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler messageHandler(ApplianceFacade applianceFacade) {
        return message -> {
            if (message.getHeaders().get("mqtt_receivedTopic") == null) {
                return;
            }
            String payload = (String) message.getPayload();
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            try {
                Map<String, Object> msgMap = OBJECT_MAPPER.readValue(payload, Map.class);
                String deviceId = topic != null && topic.startsWith("zigbee2mqtt/") && !topic.startsWith("zigbee2mqtt/bridge")
                        ? topic.split("/")[1] : null;
                eventRepository.save(Event.builder().type("inbound.mqtt.msg").utcTime(getUtc())
                        .mqttTopic(topic).device(deviceId).data(msgMap).build());
            } catch (Exception e) {
                LOGGER.warn("Failed to log inbound MQTT message payload {}", payload);
            }
            try {
                LOGGER.info("mqtt.msg.received: {}", payload);
                if (mqttTopic.equals(topic)) {
                    Map<String, Object> sensorMap = OBJECT_MAPPER.readValue(payload, new TypeReference<>() {
                    });
                    String publisherId = (String) sensorMap.get("publisherId");
                    String locationId = (String) sensorMap.get("measurePlace");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> air = (Map<String, Object>) sensorMap.get("air");
                    if (air != null) {
                        LocalDateTime utc = getUtc();
                        LocalDateTime local = toLocalDateTime(utc);
                        IndicationV3Builder builder = IndicationV3.builder()
                                .publisherId(publisherId).utcTime(utc).localTime(local).mqttTopic(topic).locationId(locationId);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> temp = (Map<String, Object>) air.get("temp");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> pressure = (Map<String, Object>) air.get("pressure");
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
                    // Ignore /set topics: they are outbound command channels. The app subscribes to
                    // zigbee2mqtt/# so it receives its own publishes and retained commands back as
                    // inbound messages, which would cause spurious state changes and false lock events.
                } else if (topic != null && !topic.startsWith("zigbee2mqtt/bridge") && !topic.endsWith("/set")) {
                    List<IndicationV3> indicationV3s = new ArrayList<>();
                    Map<String, Object> map = new ObjectMapper().readValue(payload, new TypeReference<>() {
                    });
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
                LOGGER.error("Error processing MQTT message: {}", payload, e);
            }
        };
    }

    private Double getValue(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return "ON".equalsIgnoreCase(value) ? 1.0 : 0.0;
        }
    }
}
