package com.alexsoft.smarthouse.configuration;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "smarthouse")
@Getter
public class SmarthouseConfiguration {

//    private Map<String, String> colors;
}
