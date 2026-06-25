package com.alexsoft.smarthouse.watchdog.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for managing Firebase Cloud Messaging (FCM) operations.
 * Provides endpoints for registering device tokens and sending push notifications.
 */
@RestController
@RequestMapping("/fcm")
@RequiredArgsConstructor
public class FcmController {

    private final FcmService fcmService;

    /**
     * Registers an FCM token for a device to receive push notifications.
     *
     * @param body A map containing the "token" (required) and "deviceName" (optional, defaults to "unknown").
     * @return {@link ResponseEntity} with status 200 OK if successful, or 400 Bad Request if the token is missing or blank.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> registerToken(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String deviceName = body.getOrDefault("deviceName", "unknown");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().build();
        fcmService.registerToken(token, deviceName);
        return ResponseEntity.ok().build();
    }

    /**
     * Sends an FCM push notification alert to all registered devices.
     *
     * @param body A map containing the "title" (optional), "body" (optional), and "severity" (optional) of the alert.
     * @return {@link ResponseEntity} containing a string result from the FCM service.
     */
    @PostMapping("/send")
    public ResponseEntity<String> sendAlert(@RequestBody Map<String, String> body) {
        String result = fcmService.sendAlert(
                body.getOrDefault("title", "Smarthouse Alert"),
                body.getOrDefault("body", ""),
                body.getOrDefault("severity", "severe")
        );
        return ResponseEntity.ok(result);
    }
}
