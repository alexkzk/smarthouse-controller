package com.alexsoft.smarthouse.watchdog.internal;
import com.alexsoft.smarthouse.mqtt.MessageSenderService;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.environment.IndicationServiceV3;
import com.alexsoft.smarthouse.appliance.ApplianceService;

import com.alexsoft.smarthouse.environment.IndicationV3;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.environment.IndicationRepositoryV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.alexsoft.smarthouse.appliance.ApplianceState.OFF;
import static com.alexsoft.smarthouse.appliance.ApplianceState.ON;
import static com.alexsoft.smarthouse.core.util.DateUtils.getLocalDateTime;
import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;
import static com.alexsoft.smarthouse.core.util.DateUtils.toLocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledService {

    public static final List<String> TREND_DEVICE_IDS = List.of("935-CORKWOOD-MB", "935-CORKWOOD-LR");
    public static final List<String> TREND_MEASURE_TYPES = List.of("ah", "temp");
    private final ApplianceService applianceService;
    private final ApplianceFacade applianceFacade;
    private final IndicationRepositoryV3 indicationRepositoryV3;
    private final IndicationServiceV3 indicationServiceV3;
    private final MessageSenderService messageSenderService;


    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void checkDehPowerAnomaly() {
        applianceService.getApplianceByCode("DEH").ifPresent(deh -> {
            if (deh.getState() != ON) return;
            if (deh.getSwitchedOn() == null || deh.getSwitchedOn().isAfter(getUtc().minusMinutes(5))) return;
            indicationRepositoryV3.findTopByLocationIdAndMeasurementTypeOrderByUtcTimeDesc("DEH", "power")
                    .ifPresent(last -> {
                        if (last.getValue() < 50) {
                            log.warn("deh.power.anomaly: state=ON but last power reading={}W — alerting", last.getValue());
                            //  todo create an appliance_group e.g. ALERTABLE_LIGHTS and assign in the DB instead of hardcoding here
                            messageSenderService.sendMessage("zigbee2mqtt/LED_OVER_TV/set", "{\"effect\": \"blink\"}");
                            messageSenderService.sendMessage("zigbee2mqtt/LED_UNDER_TV/set", "{\"effect\": \"blink\"}");
                            messageSenderService.sendMessage("zigbee2mqtt/LED_OVER_BED/set", "{\"effect\": \"blink\"}");
                        }
                    });
        });
    }

    @Scheduled(cron = "0/10 * * * * ?")
    public void powerControl() {
        applianceService.getAllAppliances().forEach(app -> applianceService.powerControl(app.getCode()));
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void controlFanForDeh() {
        applianceService.getApplianceByCodeReadOnly("AC").ifPresent(ac -> {
            if (ac.getState() != OFF) return;

            LocalDateTime utc = getUtc();
            LocalDateTime now = toLocalDateTime(utc);
            boolean fanNeedsToBeTurnedOn;
            if (now.getHour() > 22 || now.getHour() < 8) {
                fanNeedsToBeTurnedOn = List.of(26, 27, 28, 29, 56, 57, 58, 59).contains(now.getMinute());
            } else {
                fanNeedsToBeTurnedOn = List.of(17, 18, 19, 37, 38, 39, 57, 58, 59).contains(now.getMinute());
            }

            LocalDateTime averageStart = utc.minus(Duration.ofMinutes(ac.getAveragePeriodMinutes()));
            OptionalDouble avgDehPower = indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(
                    List.of("DEH"), averageStart, "power").stream().mapToDouble(IndicationV3::getValue).average();

            applianceService.getApplianceByCode("FAN").ifPresent(fan -> {
                ApplianceState target = avgDehPower.isPresent() && avgDehPower.getAsDouble() > 500 && fanNeedsToBeTurnedOn ? ON : OFF;
                applianceFacade.toggle(fan, target, utc, "deh-on", true);
                indicationServiceV3.save(IndicationV3.builder().publisherId("i7-4770k").measurementType("state")
                        .localTime(now).utcTime(utc).locationId("935-CORKWOOD-FAN").value(target == ON ? 1.0 : 0.0).build());
            });
        });
    }

    @Scheduled(cron = "*/5 * * * * *")
    @Transactional
    public void saveAcThresholds() {
        applianceService.getApplianceByCode("AC").ifPresent(ac -> {
            LocalDateTime utc = getUtc();
            LocalDateTime local = getLocalDateTime();
            indicationServiceV3.save(IndicationV3.builder().publisherId("i7-4770k").measurementType("temp")
                    .locationId("AC-THRESHOLD-ON").utcTime(utc).localTime(local)
                    .value(ac.getSetting() + ac.getHysteresisOn()).build());
            indicationServiceV3.save(IndicationV3.builder().publisherId("i7-4770k").measurementType("temp")
                    .locationId("AC-THRESHOLD-OFF").utcTime(utc).localTime(local)
                    .value(ac.getSetting() - ac.getHysteresisOff()).build());
        });
    }

    @Scheduled(cron = "*/3 * * * * *")
    @Transactional
    public void calculateTrends() {
        LocalDateTime utcDateTime = getUtc();
        TREND_MEASURE_TYPES.forEach(measurementType -> {
            calculateTrendAndSend(utcDateTime, 1, measurementType, TREND_DEVICE_IDS);
            calculateTrendAndSend(utcDateTime, 5, measurementType, TREND_DEVICE_IDS);
        });
    }


    public void calculateTrendAndSend(LocalDateTime utcDateTime, int minutes, String measurementType, List<String> deviceIds) {
        Comparator<IndicationV3> comparator = Comparator.comparing(IndicationV3::getUtcTime);
        List<IndicationV3> averages = indicationRepositoryV3.findByLocationIdInAndUtcTimeIsAfterAndMeasurementType(
                deviceIds, utcDateTime.minusMinutes(minutes), measurementType);
        Optional<IndicationV3> first = averages.stream().max(comparator);
        Optional<IndicationV3> second = averages.stream().min(comparator);
        if (first.map(IndicationV3::getValue).isEmpty() || second.map(IndicationV3::getValue).isEmpty()) {
            return;
        }
        long secondsDifference = Duration.between(second.get().getLocalTime(), first.get().getLocalTime()).toSeconds();
        if (secondsDifference < ((long) minutes * 60 * 0.85)) {
            return;
        }
        Double trend = (first.get().getValue() - second.get().getValue()) / secondsDifference * 3600;
        indicationServiceV3.save(IndicationV3.builder().locationId("935-CORKWOOD-TREND%d".formatted(minutes)).localTime(getLocalDateTime()).utcTime(getUtc())
                .publisherId("i7-4770k").measurementType(measurementType.equals("ah") ? "ah" : "temp").value(trend).build());
    }

}
