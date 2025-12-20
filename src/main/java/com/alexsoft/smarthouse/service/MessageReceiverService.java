package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.entity.Event;
import com.alexsoft.smarthouse.entity.Indication;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.IndicationV3.IndicationV3Builder;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.repository.EventRepository;
import com.alexsoft.smarthouse.utils.TempUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
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

import static com.alexsoft.smarthouse.utils.DateUtils.*;

@Configuration
@RequiredArgsConstructor
public class MessageReceiverService {

    public static final List<String> MEASUREMENT_TYPES = List.of("energy", "power", "state", "voltage");
    public static final Map<String, String> UNITS_MAP = Map.of("energy", "kWh", "power", "W", "voltage", "V", "illuminance", "lux");

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageReceiverService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TempUtils tempUtils = new TempUtils();
    private final ApplianceService applianceService;
    private final IndicationService indicationService;
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
                eventRepository.save(Event.builder().type("inbound.mqtt.msg").utcTime(getUtc()).data(msgMap).build());
            } catch (Exception  e) {
                LOGGER.warn("Failed to log inbound MQTT message payload {}", payload);
            }
            try {
                LOGGER.info("mqtt.msg.received: {}", payload);
                if (mqttTopic.equals(topic)) {
                    List<IndicationV3> indicationV3s = indicationService.save(toIndication(payload), topic);
                    indicationV3s.forEach(ind -> ind.setMqttTopic(topic));
                } else if (topic != null && !topic.startsWith("zigbee2mqtt/bridge")) {
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
                    if (map.containsKey("temperature")) {
                        Double temperature = getValue(String.valueOf(map.get("temperature")));
                        indicationV3s.add(indicationV3Builder.measurementType("temp").unit("c")
                                .value(temperature).build());
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

                    Optional<Appliance> applianceByCode = applianceService.getApplianceByCode(deviceId);
                    if (applianceByCode.isPresent() && map.containsKey("state")) {
                        String receivedState = (String) map.get("state");
                        Appliance appliance = applianceByCode.get();
                        if (!appliance.getState().name().equalsIgnoreCase(receivedState)) {
                            applianceFacade.toggle(appliance, ApplianceState.valueOf(receivedState), getUtc(), "mqtt-msg");
                            applianceService.saveOrUpdateAppliance(appliance);
//                            applianceService.powerControl(appliance.getCode());   //  avoid race condition
                        }
                    }

                }
            } catch (Exception e) {
                LOGGER.error("Error processing MQTT message: {}", payload, e);
            }
        };
    }

    private Indication toIndication(String payload) throws JsonProcessingException {
        LocalDateTime utc = getUtc();
        Indication indication = OBJECT_MAPPER.readValue(payload, Indication.class);
        indication.setReceivedUtc(utc);
        indication.setReceivedLocal(toLocalDateTime(utc));
        if (indication.getAggregationPeriod() == null) {
            indication.setAggregationPeriod(AggregationPeriod.INSTANT);
        }
        return indication;
    }

    private Double getValue(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return "ON".equalsIgnoreCase(value) ? 1.0 : 0.0;
        }
    }
}
