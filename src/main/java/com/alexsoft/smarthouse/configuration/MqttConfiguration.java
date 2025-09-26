package com.alexsoft.smarthouse.configuration;

import com.alexsoft.smarthouse.entity.Indication;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.entity.IndicationV3.IndicationV3Builder;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.service.IndicationServiceV3;
import com.alexsoft.smarthouse.service.MetarService;
import com.alexsoft.smarthouse.utils.DateUtils;
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
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    public static final List<String> MEASUREMENT_TYPES = List.of("energy", "power", "state", "voltage");
    public static final Map<String, String> UNITS_MAP = Map.of("energy", "kWh", "power", "W", "voltage", "V", "illuminance", "lux");
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfiguration.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IndicationRepositoryV2 indicationRepositoryV2;

    @Value("tcp://${mqtt.server-in}:${mqtt.port}")
    private String mqttUrlIn;

    @Value("tcp://${mqtt.server-out}:${mqtt.port}")
    private String mqttUrlOut;

    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

//    @Value("${mqtt.user}")
//    private String mqttUser;

//    @Value("${mqtt.password}")
//    private String mqttPassword;

    private final DateUtils dateUtils;
    private final IndicationService indicationService;
    private final IndicationServiceV3 indicationServiceV3;

    @Bean
    public DefaultMqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
//        options.setUserName("mqttUser");
//        options.setPassword("mqttPassword".toCharArray());

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
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrlOut, mqttSubscriber));
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler messageHandler() {
        return message -> {
            if (message.getPayload() == null || message.getHeaders().get("mqtt_receivedTopic") == null) {
                return;
            }
            String payload = (String) message.getPayload();
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            try {
                LOGGER.info("Received an MQTT message: {}", payload);
                LocalDateTime utcDateTime = dateUtils.getUtcLocalDateTime();
                LocalDateTime localDateTime = utcDateTime;
                if (mqttTopic.equals(topic)) {
                    Indication indication = OBJECT_MAPPER.readValue(payload, Indication.class);
                    indication.setReceivedUtc(localDateTime);
                    indication.setReceivedLocal(dateUtils.toLocalDateTime(localDateTime));
                    if (indication.getAggregationPeriod() == null) {
                        indication.setAggregationPeriod(AggregationPeriod.INSTANT);
                    }
                    indicationService.save(indication, MetarService.toIndicationV2(indication));
                } else if (!topic.startsWith("zigbee2mqtt/bridge")) {
                    Map<String, Object> map = new ObjectMapper().readValue(payload, new TypeReference<>() {
                    });
                    List<IndicationV3> indicationV3s = new ArrayList<>();
                    IndicationV3Builder indicationV3Builder = IndicationV3.builder().localTime(dateUtils.getLocalDateTime())
                            .utcTime(utcDateTime).deviceId(topic.split("/")[1]);
                    if (map.containsKey("power")) {
                        indicationV3Builder.deviceType("sp");
                        MEASUREMENT_TYPES.forEach(m -> indicationV3s.add(indicationV3Builder.measurementType(m).unit(UNITS_MAP.get(m))
                                .value(getValue(String.valueOf(map.get(m)))).build()));
                    } else if (map.containsKey("illuminance")) {
                        indicationV3s.add(indicationV3Builder.deviceType("lis").measurementType("illuminance").unit("lux")
                                .value(getValue(String.valueOf(map.get("illuminance")))).build());
                    }
                    indicationServiceV3.saveAll(indicationV3s);

//                    IndicationV3.builder().unit()

                }
            } catch (Exception e) {
                LOGGER.error("Error processing MQTT message: {}", payload, e);
            }
        };
    }

    public static Double getValue(String value) {
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return "ON".equalsIgnoreCase(value) ? 1.0 : 0.0;
        }
    }
}
