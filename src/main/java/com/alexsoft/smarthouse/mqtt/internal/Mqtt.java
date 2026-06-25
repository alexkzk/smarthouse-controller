package com.alexsoft.smarthouse.mqtt.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Mqtt {

    @Value("tcp://${mqtt.server-out}:${mqtt.port}")
    private String mqttUrlOut;
    @Value("${mqtt.subscriber}")
    private String mqttSubscriber;

    @Value("${mqtt.server-in}")
    private String mqttServerIn;
    @Value("${mqtt.topic}")
    private String mqttTopic;

    @Bean
    public org.springframework.messaging.MessageChannel mqttInputChannel() {
        return new org.springframework.integration.channel.DirectChannel();
    }

    @Bean
    public org.springframework.integration.core.MessageProducer inbound() {
        String mqttUrl = "tcp://" + mqttServerIn + ":1883";
        org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter adapter =
                new org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter(
                        mqttUrl, mqttSubscriber + "-" + java.util.UUID.randomUUID(), mqttTopic);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new org.springframework.integration.mqtt.support.DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(mqttUrlOut, mqttSubscriber);
        handler.setCompletionTimeout(5000);
        return f -> f.handle(handler);
    }
}
