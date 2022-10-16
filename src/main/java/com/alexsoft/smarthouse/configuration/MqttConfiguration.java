package com.alexsoft.smarthouse.configuration;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfiguration.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IndicationService indicationService;

    @Value("tcp://${mqtt.server}:${mqtt.port}")
    private String mqttUrl;

    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

    @Value("${mqtt.user}")
    private String mqttUser;

    @Value("${mqtt.password}")
    private String mqttPassword;

    private final DateUtils dateUtils;

    private final Map<String, String> timezoneMap = Map.of("SOUTH", "Europe/Kiev", "SEATTLE", "America/Los_Angeles");

    @Bean
    public IntegrationFlow mqttInbound() {

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setUserName(mqttUser);
        options.setPassword((mqttPassword + "#").toCharArray());

        DefaultMqttPahoClientFactory defaultMqttPahoClientFactory = new DefaultMqttPahoClientFactory();
        defaultMqttPahoClientFactory.setConnectionOptions(options);

        return IntegrationFlows.from(
                new MqttPahoMessageDrivenChannelAdapter(
                        mqttUrl, mqttSubscriber + "-" + UUID.randomUUID(), defaultMqttPahoClientFactory,
                        mqttTopic
                )
        ).handle(m -> {
            String message = String.valueOf(m.getPayload());
            LOGGER.debug("Received an MQTT message {}", message);
            try {
                Indication indication = OBJECT_MAPPER.readValue(message, Indication.class);
                indication.setReceivedUtc(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
                String indicationPlace = indication.getIndicationPlace();

                if (indicationPlace != null && timezoneMap.get(indicationPlace) != null) {
                    indication.setReceivedLocal(dateUtils.ttoLocalDateTimeAtZone(indication.getReceivedUtc(),
                            timezoneMap.get(indicationPlace)));
                } else {
                    indication.setReceivedLocal(dateUtils.toLocalDateTime(indication.getReceivedUtc()));
                }

                indicationService.save(indication, true, AggregationPeriod.INSTANT);
            } catch (JsonProcessingException e) {
                LOGGER.error("Error during reading an MQTT message", e);
            } catch (Exception e) {
                LOGGER.error("Error during saving an MQTT message", e);
            }
        }).get();
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrl, mqttSubscriber));
    }

}
