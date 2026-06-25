package com.alexsoft.smarthouse.appliance.internal;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceFacade;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.alexsoft.smarthouse.appliance.ApplianceState;
import com.alexsoft.smarthouse.core.Event;
import com.alexsoft.smarthouse.core.EventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alexsoft.smarthouse.core.util.DateUtils.getUtc;

/**
 * REST Controller for managing smart home appliances.
 * Provides endpoints to retrieve appliances and update their state and settings.
 */
@RestController
@RequestMapping("/appliances")
@RequiredArgsConstructor
public class ApplianceController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplianceController.class);
    public static final double TEMP_CONTROL_STEP = 0.25;

    private final ApplianceService applianceService;
    private final ApplianceFacade applianceFacade;
    private final EventRepository eventRepository;

    /**
     * Retrieves all appliances.
     *
     * @param requesterId Optional identifier of the requester.
     * @param request     The HTTP request object.
     * @return A list of all appliances.
     */
    @GetMapping
    public ResponseEntity<List<Appliance>> getAllAppliances(@RequestParam(required = false) String requesterId,
                                                            HttpServletRequest request) {
        Map<String, Object> eventData = new HashMap<>();
        if (requesterId != null) eventData.put("requesterId", requesterId);
        eventData.put("ip", extractIp(request));
        eventRepository.save(Event.builder().utcTime(getUtc()).type("http.request").device(null).data(eventData).build());
        List<Appliance> appliances = applianceService.getAllAppliances(requesterId);
        return ResponseEntity.ok(appliances);
    }

    /**
     * Partially updates an appliance based on the provided fields in the request body.
     *
     * @param applianceCode The code of the appliance to update.
     * @param updates       A map containing the fields to update and their new values.
     * @param requesterId   Optional identifier of the requester.
     * @param request       The HTTP request object.
     * @return The updated appliance, or 404 Not Found if the appliance does not exist.
     */
    @PatchMapping("/{applianceCode}")
    @Transactional
    public ResponseEntity<Appliance> partiallyUpdateAppliance(@PathVariable String applianceCode,
                                                               @RequestBody Map<String, Object> updates,
                                                               @RequestParam(required = false) String requesterId,
                                                               HttpServletRequest request) {
        String ip = extractIp(request);
        return applianceService.getApplianceByCode(applianceCode).map(appliance -> {
            updates.forEach((key, value) -> {
                switch (key) {
                    case "powerSetting":
                        appliance.setPowerSetting((Double) value);
                        break;
                    case "description":
                        appliance.setDescription((String) value);
                        break;
                    case "state":
                        ApplianceState newState = ApplianceState.valueOf((String) value);
                        applianceFacade.toggle(appliance, newState, getUtc(), "http-controller", true);
                        break;
                    case "consumptionKwh":
                        appliance.setConsumptionKwh(Double.valueOf(value.toString()));
                        break;
                    case "locked":
                        Boolean locked = (Boolean) value;
                        appliance.setLocked(locked);
                        eventRepository.save(Event.builder().utcTime(getUtc())
                                .type("http.locked").device(applianceCode)
                                .data(dataWithRequester(Map.of("locked", locked), requesterId, ip)).build());
                        break;
                    case "lockedUntil":
                        String lockedUntil = (String) value;
                        if (lockedUntil.equals("null")) {
                            appliance.setLockedUntilUtc(null);
                            eventRepository.save(Event.builder().utcTime(getUtc())
                                    .type("http.lockedUntil.cleared").device(applianceCode)
                                    .data(dataWithRequester(Map.of(), requesterId, ip)).build());
                        } else {
                            LocalDateTime selectedLockedUntil = LocalDateTime.parse(lockedUntil, DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
                            LocalDateTime previousLock = appliance.getLockedUntilUtc();
                            if (appliance.getLockedUntilUtc() == null) {
                                appliance.setLockedUntilUtc(selectedLockedUntil);
                            } else {
                                Duration duration = Duration.between(LocalDateTime.now(), selectedLockedUntil);
                                appliance.setLockedUntilUtc(appliance.getLockedUntilUtc().plus(duration));
                            }
                            eventRepository.save(Event.builder().utcTime(getUtc())
                                    .type("http.lockedUntil").device(applianceCode)
                                    .data(dataWithRequester(Map.of("requested", lockedUntil, "previous", String.valueOf(previousLock), "result", appliance.getLockedUntilUtc().toString()), requesterId, ip)).build());
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
                    case "hysteresisOn":
                        appliance.setHysteresisOn(Double.valueOf(value.toString()));
                        break;
                    case "hysteresisOff":
                        appliance.setHysteresisOff(Double.valueOf(value.toString()));
                        break;
                    case "referenceSensors":
                        appliance.setReferenceSensors((List<String>) value);
                        break;
                    default:
                        String errorMessage = String.format("Field '%s' is not supported for updating", key);
                        LOGGER.warn(errorMessage);
                        throw new IllegalArgumentException(errorMessage);
                }
            });

            Map<String, Object> eventData = new HashMap<>(updates);
            if (updates.containsKey("setting") && ("+".equals(updates.get("setting")) || "-".equals(updates.get("setting")))){
                eventData.put("settingValue", appliance.getSetting());
            }
            if (requesterId != null) eventData.put("requesterId", requesterId);
            eventData.put("ip", ip);
            eventRepository.save(Event.builder().utcTime(getUtc()).type("http.request").device(applianceCode).data(eventData).build());

            Appliance updatedAppliance = applianceService.saveOrUpdateAppliance(appliance);
            postCommitPowerControl(applianceCode);
            return ResponseEntity.ok(updatedAppliance);
        }).orElse(ResponseEntity.notFound().build());
    }

    // Non-Transactional Method to Invoke Power Control
    private void postCommitPowerControl(String applianceCode) {
        // Ensure that this method is called after the transaction from the main method has been committed
        applianceService.powerControl(applianceCode);
    }


    /**
     * Increases the given temperature by the defined control step.
     *
     * @param currentTemperature The current temperature.
     * @return The increased temperature rounded to the nearest half.
     */
    public static Double increaseTemperature(Double currentTemperature) {
        return roundToNearestHalf(currentTemperature) + TEMP_CONTROL_STEP;
    }

    /**
     * Decreases the given temperature by the defined control step.
     *
     * @param currentTemperature The current temperature.
     * @return The decreased temperature rounded to the nearest half.
     */
    public static Double decreaseTemperature(Double currentTemperature) {
        double roundedTemperature = roundToNearestHalf(currentTemperature);
        if (currentTemperature > roundedTemperature) {
            return roundedTemperature;
        }
        return roundedTemperature - TEMP_CONTROL_STEP;
    }

    private static double roundToNearestHalf(double value) {
        return Math.round(value * 4) / 4.0;
    }

    private static Map<String, Object> dataWithRequester(Map<String, Object> base, String requesterId, String ip) {
        Map<String, Object> data = new HashMap<>(base);
        if (requesterId != null) data.put("requesterId", requesterId);
        data.put("ip", ip);
        return data;
    }

    private static String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }



}
