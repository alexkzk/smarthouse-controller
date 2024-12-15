package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.db.entity.Appliance;
import com.alexsoft.smarthouse.db.entity.IndicationV2;
import com.alexsoft.smarthouse.db.repository.ApplianceRepository;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static com.alexsoft.smarthouse.enums.ApplianceState.OFF;
import static com.alexsoft.smarthouse.enums.ApplianceState.ON;

@Service
@RequiredArgsConstructor
public class PowerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PowerController.class);
    public static final String FREQUENCY_MINUTES = "1";
    public static final List<String> REFERENCE_HUMID_SENSORS = List.of("APT2107S-MB");
    public static final Duration AVERAGING_PERIOD = Duration.ofMinutes(5);

    private final DateUtils dateUtils;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final ApplianceService applianceService;
    private final ApplianceRepository applianceRepository;



    @Scheduled(cron = "0 0/" + FREQUENCY_MINUTES + " * * * ?")
    public void powerControl() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(REFERENCE_HUMID_SENSORS,
                localDateTime.minus(AVERAGING_PERIOD));
        String applianceCode = "AC";
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        if (CollectionUtils.isEmpty(indications)) {
            appliance.setState(OFF, localDateTime);
            appliance.setStatusUpdated(localDateTime);
            LOGGER.info("Power control method executed, indications were empty");
        } else {
            try {
                double ah = indications.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow();
                LOGGER.info("Power control method executed, ah was: {}", ah);
                if (ah > appliance.getSetting() + appliance.getHysteresis()) {
                    appliance.setState(ON, localDateTime);
                    appliance.setStatusUpdated(localDateTime);
                } else if (ah < appliance.getSetting() - appliance.getHysteresis()) {
                    appliance.setState(OFF, localDateTime);
                    appliance.setStatusUpdated(localDateTime);
                }
            } catch (Exception e) {
                LOGGER.error("Error during calculating average absolute humidity", e);
            }
        }
        applianceRepository.save(appliance);
        applianceService.switchAppliance(appliance, localDateTime);
    }

}
