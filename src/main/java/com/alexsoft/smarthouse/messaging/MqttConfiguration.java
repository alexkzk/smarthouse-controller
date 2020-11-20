package com.alexsoft.smarthouse.messaging;

import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.service.HouseStateService;
import com.alexsoft.smarthouse.utils.HouseStateMsgConverter;
import lombok.AllArgsConstructor;
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

import java.util.UUID;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfiguration.class);

    private final HouseStateMsgConverter houseStateMsgConverter;
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

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

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
            houseStateService.save(houseStateMsgConverter.toEntity(message));
        }).get();
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrl, mqttSubscriber));
    }

}
