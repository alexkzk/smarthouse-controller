package com.alexsoft.smarthouse.watchdog.internal;
import com.alexsoft.smarthouse.watchdog.internal.FcmService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentAvailabilityService {

    private final FcmService fcmService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String API_URL = "https://doorway-api.knockrentals.com/v1/property/2012283/units";

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Running initial apartment availability check on startup...");
        checkAvailability();
    }

    // Runs twice a day at 10:00 AM and 6:00 PM
    @Scheduled(cron = "0 0 10,18 * * *")
    public void onSchedule() {
        log.info("Running scheduled 10:00 AM / 6:00 PM apartment availability check...");
        checkAvailability();
    }

    public void checkAvailability() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to fetch apartment data. Status code: {}", response.statusCode());
                return;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode unitsNode = root.path("units_data").path("units");

            if (unitsNode.isMissingNode() || !unitsNode.isArray()) {
                log.error("Invalid JSON structure: 'units' array not found.");
                return;
            }

            int availableCount = 0;
            boolean found1065Unit = false;

            for (JsonNode unit : unitsNode) {
                boolean isAvailable = unit.path("available").asBoolean(false);
                boolean isLeased = unit.path("leased").asBoolean(true);
                String price = unit.path("price").asText();

                if (isAvailable && !isLeased && price != null && !price.isEmpty() && !price.equals("null")) {
                    availableCount++;
                    int area = unit.path("area").asInt(0);
                    if (area == 1065) {
                        found1065Unit = true;
                    }
                }
            }

            String body = String.format("Found %d available apartments.", availableCount);
            if (found1065Unit) {
                body += " GOOD NEWS: A 1065 sqft apartment was found!";
            } else {
                body += " No 1065 sqft apartments were found.";
            }

            log.info("Apartment check complete: {}", body);

            // Send push notification using FcmService
            fcmService.sendAlert("Apartment Availability", body, "info");

        } catch (Exception e) {
            log.error("Error occurred while checking apartment availability", e);
        }
    }
}
