package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.db.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/appliances")
@RequiredArgsConstructor
public class ApplianceController {
    Logger logger = LoggerFactory.getLogger(ApplianceController.class);

    private final ApplianceService applianceService;
    private final DateUtils dateUtils;

    @GetMapping
    public ResponseEntity<List<Appliance>> getAllAppliances() {
        List<Appliance> appliances = applianceService.getAllAppliances();
        return ResponseEntity.ok(appliances);
    }

    @PatchMapping("/{code}")
    public ResponseEntity<Appliance> partiallyUpdateAppliance(@PathVariable String code, @RequestBody Map<String, Object> updates) {
        LocalDateTime localDateTime = dateUtils.toLocalDateTime(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
        if(code.contains("test")) {
            logger.info("Received test update for appliance with code '{}', updates: {}", code, updates);
            return ResponseEntity.ok().build();
        }
        return applianceService.getApplianceByCode(code).map(appliance -> {
            updates.forEach((key, value) -> {
                logger.debug("Processing update for field: '{}'", key); // Log which field is being processed

                switch (key) {
                    case "description":
                        logger.debug("Updating 'description' from '{}' to '{}'", appliance.getDescription(), value);
                        appliance.setDescription((String) value);
                        break;
                    case "state":
                        ApplianceState newState = ApplianceState.valueOf((String) value);
                        logger.debug("Updating 'state' from '{}' to '{}'", appliance.getState(), newState);
                        appliance.setState(newState, LocalDateTime.now());
                        break;
                    case "consumptionKwh":
                        logger.debug("Updating 'consumptionKwh' from '{}' to '{}'", appliance.getConsumptionKwh(), value);
                        appliance.setConsumptionKwh(Double.valueOf(value.toString()));
                        break;
                    case "locked":
                        Boolean locked = (Boolean) value;
                        logger.debug("Updating 'locked' field to: '{}'", locked);
                        appliance.setLocked(locked);
                        if (locked) {
                            appliance.setLockedUntil(localDateTime);
                            logger.debug("Set 'lockedAt' timestamp to: '{}'", localDateTime);
                        } else {
                            appliance.setLockedUntil(null);
                            logger.debug("'lockedAt' timestamp cleared");
                        }
                        break;
                    case "setting":
                        logger.debug("Updating 'setting' from '{}' to '{}'", appliance.getSetting(), value);
                        appliance.setSetting(Double.valueOf(value.toString()));
                        break;
                    case "hysteresis":
                        logger.debug("Updating 'hysteresis' from '{}' to '{}'", appliance.getHysteresis(), value);
                        appliance.setHysteresis(Double.valueOf(value.toString()));
                        break;
                    case "referenceSensors":
                        logger.debug("Updating 'referenceSensors' to: '{}'", value);
                        appliance.setReferenceSensors((List<String>) value);
                        break;
                    default:
                        String errorMessage = String.format("Field '%s' is not supported for updating", key);
                        logger.warn(errorMessage);
                        throw new IllegalArgumentException(errorMessage);
                }
            });

            Appliance updatedAppliance = applianceService.saveOrUpdateAppliance(appliance);
            logger.info("Successfully updated appliance with code '{}': {}", code, updatedAppliance);
            applianceService.powerControl();

            return ResponseEntity.ok(updatedAppliance);
        }).orElse(ResponseEntity.notFound().build());
    }

}
