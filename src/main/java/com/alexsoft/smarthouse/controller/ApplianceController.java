package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.db.entity.Appliance;
import com.alexsoft.smarthouse.enums.ApplianceState;
import com.alexsoft.smarthouse.service.ApplianceService;
import com.alexsoft.smarthouse.utils.DateUtils;
import lombok.RequiredArgsConstructor;
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
        return applianceService.getApplianceByCode(code).map(appliance -> {
            updates.forEach((key, value) -> {
                switch (key) {
                    case "description":
                        appliance.setDescription((String) value);
                        break;
                    case "state":
                        appliance.setState(ApplianceState.valueOf((String) value), LocalDateTime.now());
                        break;
                    case "consumptionKwh":
                        appliance.setConsumptionKwh(Double.valueOf(value.toString()));
                        break;
                    case "locked":
                        Boolean locked = (Boolean) value;
                        appliance.setLocked(locked);
                        if (locked) {
                            appliance.setLockedAt(localDateTime);
                        } else {
                            appliance.setLockedAt(null);
                        }
                        appliance.setLockedAt(LocalDateTime.now());
                        break;
                    case "setting":
                        appliance.setSetting(Double.valueOf(value.toString()));
                        break;
                    case "hysteresis":
                        appliance.setHysteresis(Double.valueOf(value.toString()));
                        break;
                    case "referenceSensors":
                        appliance.setReferenceSensors((List<String>) value);
                        break;
                    default:
                        throw new IllegalArgumentException("Field '" + key + "' is not supported for updating");
                }
            });

            Appliance updatedAppliance = applianceService.saveOrUpdateAppliance(appliance);
            return ResponseEntity.ok(updatedAppliance);
        }).orElse(ResponseEntity.notFound().build());
    }

}
