package com.alexsoft.smarthouse.watchdog;

import com.alexsoft.smarthouse.service.FcmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class WatchdogStartupTestRunner implements CommandLineRunner {

    private final FcmService fcmService;

    @Override
    public void run(String... args) {
        log.info("Watchdog Startup Test Runner initialized. Will trigger startup notification in 15 seconds...");
        
        // Execute in a separate thread to prevent blocking main Spring Boot startup lifecycle
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait 2 seconds for container network routes to settle
                
                log.info("Sending startup notification to registered devices...");
                String result = fcmService.sendAlert("SmartHouse Central", "Smarthouse Backend restarted", "sound");
                log.info("Startup notification sent. Result: {}", result);
            } catch (InterruptedException e) {
                log.warn("Watchdog startup test runner was interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Watchdog startup notification execution encountered an error: {}", e.getMessage(), e);
            }
        }).start();
    }
}
