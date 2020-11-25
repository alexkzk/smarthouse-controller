package com.alexsoft.smarthouse.messaging;

import java.util.UUID;

import com.alexsoft.smarthouse.service.HouseState2Service;
import com.alexsoft.smarthouse.service.HouseStateService;
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

    private final HouseState2Service houseState2Service;
    private final HouseStateService houseStateService;

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

    @Bean
    public IntegrationFlow mqttInbound() {

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setUserName(mqttUser);
        options.setPassword(mqttPassword.toCharArray());

        DefaultMqttPahoClientFactory defaultMqttPahoClientFactory = new DefaultMqttPahoClientFactory();
        defaultMqttPahoClientFactory.setConnectionOptions(options);

        return IntegrationFlows.from(
            new MqttPahoMessageDrivenChannelAdapter(mqttUrl, mqttSubscriber + "-" + UUID.randomUUID(),
                defaultMqttPahoClientFactory, mqttTopic)
        ).handle(m -> {
            String message = String.valueOf(m.getPayload());
            LOGGER.debug("Received a message {}", message);
            if (message.contains("{")) {
                houseState2Service.save(message);
            } else {
                houseStateService.save(message);
            }
        }).get();
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrl, mqttSubscriber));
    }

}
