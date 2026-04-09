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
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(mqttUrlOut, mqttSubscriber);
        handler.setCompletionTimeout(5000);
        return f -> f.handle(handler);
    }

}
