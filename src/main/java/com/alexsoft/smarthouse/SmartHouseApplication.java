package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MetarLocationsConfig.class)
public class SmartHouseApplication {

    private static final String BANNER = "\n\n"
        + "▒▒▒▒▒▒▒╗▒▒▒╗   ▒▒▒╗ ▒▒▒▒▒╗ ▒▒▒▒▒▒╗ ▒▒▒▒▒▒▒▒╗▒▒╗  ▒▒╗ ▒▒▒▒▒▒╗ ▒▒╗   ▒▒╗▒▒▒▒▒▒▒╗▒▒▒▒▒▒▒╗    ▒▒▒▒▒▒╗  ▒▒▒▒▒▒╗  ▒▒▒▒▒▒╗▒▒╗  ▒▒╗▒▒▒▒▒▒▒╗▒▒╗\n"
        + "▒▒╔════╝▒▒▒▒╗ ▒▒▒▒║▒▒╔══▒▒╗▒▒╔══▒▒╗╚══▒▒╔══╝▒▒║  ▒▒║▒▒╔═══▒▒╗▒▒║   ▒▒║▒▒╔════╝▒▒╔════╝    ▒▒╔══▒▒╗▒▒╔═══▒▒╗▒▒╔════╝▒▒║ ▒▒╔╝▒▒╔════╝▒▒║\n"
        + "▒▒▒▒▒▒▒╗▒▒╔▒▒▒▒╔▒▒║▒▒▒▒▒▒▒║▒▒▒▒▒▒╔╝   ▒▒║   ▒▒▒▒▒▒▒║▒▒║   ▒▒║▒▒║   ▒▒║▒▒▒▒▒▒▒╗▒▒▒▒▒╗      ▒▒▒▒▒▒╔╝▒▒║   ▒▒║▒▒║     ▒▒▒▒▒╔╝ ▒▒▒▒▒▒▒╗▒▒║\n"
        + "╚════▒▒║▒▒║╚▒▒╔╝▒▒║▒▒╔══▒▒║▒▒╔══▒▒╗   ▒▒║   ▒▒╔══▒▒║▒▒║   ▒▒║▒▒║   ▒▒║╚════▒▒║▒▒╔══╝      ▒▒╔══▒▒╗▒▒║   ▒▒║▒▒║     ▒▒╔═▒▒╗ ╚════▒▒║╚═╝\n"
        + "▒▒▒▒▒▒▒║▒▒║ ╚═╝ ▒▒║▒▒║  ▒▒║▒▒║  ▒▒║   ▒▒║   ▒▒║  ▒▒║╚▒▒▒▒▒▒╔╝╚▒▒▒▒▒▒╔╝▒▒▒▒▒▒▒║▒▒▒▒▒▒▒╗    ▒▒║  ▒▒║╚▒▒▒▒▒▒╔╝╚▒▒▒▒▒▒╗▒▒║  ▒▒╗▒▒▒▒▒▒▒║▒▒╗\n"
        + "╚══════╝╚═╝     ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝ ╚═════╝  ╚═════╝ ╚══════╝╚══════╝    ╚═╝  ╚═╝ ╚═════╝  ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝\n"
        + "::  By AlexKZK, 2020  ::                                                                                                              \n";

    private static final Logger LOGGER = LoggerFactory.getLogger("SmartHouseApplication");

    public static void main(String[] args) {
        SpringApplication.run(SmartHouseApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logBanner() {
        LOGGER.info(BANNER);
    }

}
