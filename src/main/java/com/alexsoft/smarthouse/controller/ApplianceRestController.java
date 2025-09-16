package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.service.ApplianceService;
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

    @GetMapping
    public ResponseEntity<List<Appliance>> getAllAppliances() {
        List<Appliance> appliances = applianceService.getAllAppliances();
        return ResponseEntity.ok(appliances);
    }

    @PatchMapping("/{code}")
    @Transactional
    public ResponseEntity<Appliance> partiallyUpdateAppliance(@PathVariable String code, @RequestBody Map<String, Object> updates) {
        ZonedDateTime utc = ZonedDateTime.now(ZoneId.of("UTC"));
        if(code.contains("test")) {
            logger.info("Received test update for appliance with code '{}', updates: {}", code, updates);
            return ResponseEntity.ok().build();
        }
        return applianceService.getApplianceByCode(code).map(appliance -> {
            updates.forEach((key, value) -> {

                switch (key) {
                    case "description":
                        appliance.setDescription((String) value);
                        break;
                    case "state":
                        ApplianceState newState = ApplianceState.valueOf((String) value);
                        appliance.setState(newState, LocalDateTime.now());
                        appliance.setLockedUntilUtc(utc.toLocalDateTime().plusMinutes(5));
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
                            appliance.setSetting(appliance.getSetting() + appliance.getHysteresis());
                        } else if(value.equals("-")) {
                            appliance.setSetting(appliance.getSetting() - appliance.getHysteresis());
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

}
