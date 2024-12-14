package com.alexsoft.smarthouse.configuration;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.db.entity.IndicationV2;
import com.alexsoft.smarthouse.db.entity.Measurement;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
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
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static com.alexsoft.smarthouse.utils.Constants.S_OCEAN_DR_HOLLYWOOD;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfiguration.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC = "mqtt.smarthouse.power.control";

    private final IndicationRepositoryV2 indicationRepositoryV2;

    @Value("tcp://${mqtt.server}:${mqtt.port}")
    private String mqttUrl;

    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

//    @Value("${mqtt.user}")
//    private String mqttUser;

//    @Value("${mqtt.password}")
//    private String mqttPassword;

    private final DateUtils dateUtils;
    private final IndicationService indicationService;

    private String powerState;
//    private final IntegrationFlow mqttOutboundFlow;

    public void sendMessage(String topic, String messagePayload) {
        LOGGER.info("Sending MQTT message: topic={}, payload={}", topic, messagePayload);
        mqttOutboundFlow().getInputChannel().send(MessageBuilder.withPayload(messagePayload).setHeader("mqtt_topic", topic).build());
    }

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
                new MqttPahoMessageDrivenChannelAdapter(mqttUrl, mqttSubscriber + "-" + UUID.randomUUID(), mqttTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrl, mqttSubscriber));
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler messageHandler() {
        return message -> {
            String payload = (String) message.getPayload();
            LOGGER.debug("Received an MQTT message: {}", payload);
            try {
                Indication indication = OBJECT_MAPPER.readValue(payload, Indication.class);
                processIndication(indication);
            } catch (JsonProcessingException e) {
                LOGGER.error("Error processing MQTT message: {}", payload, e);
            }
        };
    }

    @Scheduled(cron = "0 0/1 * * * ?")
    public void powerControl() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(List.of("APT2107S-MB", "APT2107S-B"),
                localDateTime.minusMinutes(5));
        if (CollectionUtils.isEmpty(indications)) {
            powerState = "off";
            sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"AC\",\"state\":\"off\"}");
            LOGGER.info("Power control method executed, indications were empty");
        }
        double ah = indications.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow();
        LOGGER.info("Power control method executed, ah was: {}", ah);
        if (ah > 12) {
            powerState = "on";
            sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"AC\",\"state\":\"on\"}");
        } else if (ah < 11) {
            powerState = "off";
            sendMessage(MQTT_SMARTHOUSE_POWER_CONTROL_TOPIC, "{\"device\":\"AC\",\"state\":\"off\"}");
        }
        Measurement humValue = new Measurement().setValue("on".equals(powerState) ? 10.0 : 0.0);
        try {
            indicationRepositoryV2.save(new IndicationV2().setIndicationPlace("APT2107S-HUM").setLocalTime(localDateTime)
                    .setPublisherId("PI4").setInOut("IN").setAggregationPeriod("INSTANT").setTemperature(humValue).setAbsoluteHumidity(humValue));
        } catch (Exception e) {
            LOGGER.error("Error during saving humidity measurement: {}", humValue, e);
        }
    }

    private void processIndication(Indication indication) {
        try {
            if ("HOLLYWOOD-FL".equals(indication.getIndicationPlace())) {
                indication.setIndicationPlace(S_OCEAN_DR_HOLLYWOOD);
            }
            indication.setReceivedUtc(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
            indication.setReceivedLocal(dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime()));
            indicationService.save(indication, true, AggregationPeriod.INSTANT);
        } catch (Exception e) {
            LOGGER.error("Error during processing indication: {}", indication, e);
        }
    }
}
