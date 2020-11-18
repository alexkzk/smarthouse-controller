package com.alexsoft.smarthouse.messaging;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.WindIndication;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.model.Metar;
import com.alexsoft.smarthouse.service.MetarReceiver;
import com.alexsoft.smarthouse.utils.HouseStateMsgConverter;
import com.alexsoft.smarthouse.utils.TempUtils;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;

@Configuration
public class MqttConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttConfiguration.class);

    private final TempUtils tempUtils = new TempUtils();

    @Autowired
    private HouseStateRepository houseStateRepository;

    @Autowired
    private MetarReceiver metarReceiver;

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
            LOGGER.debug("Received a message {}", m.getPayload());
            String message = String.valueOf(m.getPayload());
            HouseState houseState = HouseStateMsgConverter.toEntity(message);
            if (message.contains("pm25")) { // todo replace this condition by adding measure place in message
                try {
                    Metar metar = metarReceiver.getMetar();
                    if (metarIsNotExpired(metar)) {
                        Float temp = Float.valueOf(metar.getTemperature().getValue());
                        Integer devpoint = metar.getDewpoint().getValue();
                        Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
                        HeatIndication heatIndication = HeatIndication.builder()
                            .measurePlace(MeasurePlace.CHERNIVTSI_AIRPORT)
                            .tempCelsius(temp)
                            .relativeHumidity(rh)
                            .absoluteHumidity(tempUtils.calculateAbsoluteHumidity(temp, rh))
                            .build();
                        houseState.addIndication(heatIndication);
                        if((metar.getWindDirection() != null && metar.getWindDirection().getValue() != null) ||
                            (metar.getWindSpeed() != null && metar.getWindSpeed().getValue() != null)) {
                            WindIndication windIndication = WindIndication.builder()
                                .direction(metar.getWindDirection() == null ? null : metar.getWindDirection().getValue())
                                .speed(metar.getWindSpeed() == null ? null : metar.getWindSpeed().getValue())
                                .measurePlace(MeasurePlace.CHERNIVTSI_AIRPORT)
                                .build();
                            houseState.addIndication(windIndication);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Couldn't retrieve a metar", e);
                }
            }
            if (!houseState.isNull() && msgSavingEnabled) {
                houseStateRepository.saveAndFlush(houseState);
            } else if (!houseState.isNull()) {
                LOGGER.debug("Skipping saving a null HouseState {}", houseState);
            }
        }).get();
    }

    private boolean metarIsNotExpired(final Metar metar) {
        return metar != null && metar.getTime() != null && metar.getTime().getIssueDateTime() != null &&
            ChronoUnit.HOURS.between(metar.getTime().getIssueDateTime(), ZonedDateTime.now()) < 1;
    }

    @Bean
    public IntegrationFlow mqttOutboundFlow() {
        return f -> f.handle(new MqttPahoMessageHandler(mqttUrl, mqttSubscriber));
    }

}
