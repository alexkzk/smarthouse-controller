package com.alexsoft.smarthouse.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.stereotype.Service;

@Service
public class Mqtt {

    @Value("tcp://${mqtt.server-out}:${mqtt.port}")
    private String mqttUrlOut;
    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrlOut, mqttSubscriber));
    }

}
