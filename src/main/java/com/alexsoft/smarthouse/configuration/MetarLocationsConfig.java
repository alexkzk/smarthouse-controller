package com.alexsoft.smarthouse.configuration;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "metar-locations")
@Data
public class MetarLocationsConfig {
    private Map<String, Map<String, String>> locationMapping;
}
