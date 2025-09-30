package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/appliances")
@RequiredArgsConstructor
public class ApplianceRestController {
    private static final Logger logger = LoggerFactory.getLogger(ApplianceRestController.class);

    private final ApplianceService applianceService;
    private final DateUtils dateUtils;

    @GetMapping
    public ResponseEntity<List<Appliance>> getAllAppliances() {
        List<Appliance> appliances = applianceService.getAllAppliances();
        return ResponseEntity.ok(appliances);
    }

    @PatchMapping("/{applianceCode}")
    @Transactional
    public ResponseEntity<Appliance> partiallyUpdateAppliance(@PathVariable String applianceCode, @RequestBody Map<String, Object> updates) {
        return applianceService.getApplianceByCode(applianceCode).map(appliance -> {
            updates.forEach((key, value) -> {
                switch (key) {
                    case "description":
                        appliance.setDescription((String) value);
                        break;
                    case "state":
                        ApplianceState newState = ApplianceState.valueOf((String) value);
                        appliance.setState(newState, LocalDateTime.now());
                        if (applianceCode.equals("DEH") || applianceCode.equals("AC")) {
                            appliance.setLockedUntilUtc(dateUtils.getUtcLocalDateTime().plusMinutes(5));
                        } else if (applianceCode.equals("LR-LUTV")){
                            appliance.setLockedUntilUtc(sevenAm());
                        }
                        appliance.setLocked(true);
                        break;
                    case "consumptionKwh":
                        appliance.setConsumptionKwh(Double.valueOf(value.toString()));
                        break;
                    case "locked":
                        Boolean locked = (Boolean) value;
                        appliance.setLocked(locked);
                        break;
                    case "lockedUntil":
                        String lockedUntil = (String) value;
                        if (lockedUntil.equals("null")) {
                            appliance.setLockedUntilUtc(null);
                        } else {
                            LocalDateTime selectedLockedUntil = LocalDateTime.parse(lockedUntil, DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
                            if (appliance.getLockedUntilUtc() == null) {
                                appliance.setLockedUntilUtc(selectedLockedUntil);
                            } else {
                                Duration duration = Duration.between(LocalDateTime.now(), selectedLockedUntil);
                                appliance.setLockedUntilUtc(appliance.getLockedUntilUtc().plus(duration));
                            }
                        }
                        break;
                    case "setting":
                        if (value.equals("+")) {
                            appliance.setSetting(increaseTemperature(appliance.getSetting()));
                        } else if (value.equals("-")) {
                            appliance.setSetting(decreaseTemperature(appliance.getSetting()));
                        } else {
                            appliance.setSetting(Double.valueOf(value.toString()));
                        }
                        break;
                    case "hysteresis":
                        appliance.setHysteresis(Double.valueOf(value.toString()));
                        break;
                    case "referenceSensors":
                        appliance.setReferenceSensors((List<String>) value);
                        break;
                    default:
                        String errorMessage = String.format("Field '%s' is not supported for updating", key);
                        logger.warn(errorMessage);
                        throw new IllegalArgumentException(errorMessage);
                }
            });

            Appliance updatedAppliance = applianceService.saveOrUpdateAppliance(appliance);
            applianceService.powerControl(updatedAppliance.getCode());

            return ResponseEntity.ok(updatedAppliance);
        }).orElse(ResponseEntity.notFound().build());
    }

    public LocalDateTime sevenAm() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));

        // Set target time to 6:30 AM
        ZonedDateTime sixThirtyAm = now.withHour(6).withMinute(30).withSecond(0).withNano(0);
        if (now.getHour() > 6 || (now.getHour() == 6 && now.getMinute() >= 30)) {
            // If current time is past 6:30 AM, move to the next day
            sixThirtyAm = sixThirtyAm.plusDays(1);
        }

        // Convert to UTC
        ZonedDateTime next6_30AMUtc = sixThirtyAm.withZoneSameInstant(ZoneId.of("UTC"));

        return next6_30AMUtc.toLocalDateTime();

    }


    public static Double increaseTemperature(Double currentTemperature) {
        return roundToNearestHalf(currentTemperature) + 0.5;
    }

    public static Double decreaseTemperature(Double currentTemperature) {
        double roundedTemperature = roundToNearestHalf(currentTemperature);
        if (currentTemperature > roundedTemperature) {
            return roundedTemperature;
        }
        return roundedTemperature - 0.5;
    }

    private static double roundToNearestHalf(double value) {
        return Math.round(value * 2) / 2.0;
    }



}
