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
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public static final String POWER_CHECK_FREQUENCY_MINUTES = "1";
    public static final String POWER_CHECK_CRON_EXPRESSION = "0 0/" + POWER_CHECK_FREQUENCY_MINUTES + " * * * ?";
    public static final Duration AVERAGING_PERIOD = Duration.ofMinutes(5);

    private final DateUtils dateUtils;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final ApplianceService applianceService;
    private final ApplianceRepository applianceRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void sendLastState() {
        Appliance appliance = applianceRepository.findById("AC").orElseThrow();
        LOGGER.info("Sending previous {} state", appliance.getDescription());
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        applianceService.switchAppliance(appliance, localDateTime);
    }

    @Scheduled(cron = POWER_CHECK_CRON_EXPRESSION)
    public void powerControl() {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        LocalDateTime averagingStartDateTime = localDateTime.minus(AVERAGING_PERIOD);
        String applianceCode = "AC";
        Appliance appliance = applianceRepository.findById(applianceCode).orElseThrow();
        List<IndicationV2> indications = indicationRepositoryV2.findByIndicationPlaceInAndLocalTimeIsAfter(appliance.getReferenceSensors(), averagingStartDateTime);
        if (CollectionUtils.isEmpty(indications)) {
            appliance.setState(OFF, localDateTime);
            LOGGER.info("Power control method executed, indications were empty");
        } else {
            try {
                double ah = BigDecimal.valueOf(indications.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElseThrow())
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
                LOGGER.info("Power control method executed, ah was: \u001B[34m{}\u001B[0m, the appliance's setting: {}, hysteresis: {}",
                        ah, appliance.getSetting(), appliance.getHysteresis());
                if (ah > appliance.getSetting() + appliance.getHysteresis()) {
                    appliance.setState(ON, localDateTime);
                } else if (ah < appliance.getSetting() - appliance.getHysteresis()) {
                    appliance.setState(OFF, localDateTime);
                }
            } catch (Exception e) {
                LOGGER.error("Error during calculating average absolute humidity", e);
            }
        }
        applianceRepository.save(appliance);
        applianceService.switchAppliance(appliance, localDateTime);
    }

}
