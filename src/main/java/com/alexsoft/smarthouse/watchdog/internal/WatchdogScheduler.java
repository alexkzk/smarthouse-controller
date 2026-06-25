package com.alexsoft.smarthouse.watchdog.internal;

import com.alexsoft.smarthouse.watchdog.resolver.StateResolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WatchdogScheduler {

    private final WatchdogJobRepository jobRepository;
    private final WatchdogLogRepository logRepository;
    private final StateResolutionService stateResolutionService;
    private final GeminiService geminiService;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 * * * * *") // Triggers every minute on second 0
    public void evaluateWatchdogs() {
        List<WatchdogJob> activeJobs = jobRepository.findByEnabledTrue();
        LocalDateTime now = LocalDateTime.now();

        for (WatchdogJob job : activeJobs) {
            try {
                // Parse Cron and check if it triggers in the current minute
                CronExpression cron = CronExpression.parse(job.getCronExpression());
                LocalDateTime nextExecution = cron.next(now.minusSeconds(1));

                if (nextExecution != null && 
                    nextExecution.getHour() == now.getHour() && 
                    nextExecution.getMinute() == now.getMinute()) {
                    
                    log.info("Executing Watchdog job: '{}'", job.getName());
                    // Run execution in a separate try-catch block so one failing job doesn't halt others
                    executeJob(job);
                }
            } catch (Exception e) {
                log.error("Failed executing watchdog job '{}': {}", job.getName(), e.getMessage());
            }
        }
    }

    public void executeJob(WatchdogJob job) {
        WatchdogLog watchdogLog = WatchdogLog.builder()
                .job(job)
                .executedAt(LocalDateTime.now())
                .status("PENDING")
                .build();

        String gatheredState = "{}";
        try {
            // Step 1: Gather State using custom Strategy Protocol Resolver
            if (job.getStateUrl() != null && !job.getStateUrl().isBlank()) {
                gatheredState = stateResolutionService.gatherState(job.getStateUrl());
            }
            watchdogLog.setGatheredState(gatheredState);

            // Step 2: Evaluate state via Gemini AI
            String aiResponseRaw = geminiService.evaluateState(job.getPromptTemplate(), gatheredState);
            watchdogLog.setAiResponse(aiResponseRaw);

            // Step 3: Parse AI structured JSON evaluation
            Map<String, Object> aiMap = objectMapper.readValue(aiResponseRaw, Map.class);
            boolean shouldNotify = Boolean.TRUE.equals(aiMap.get("shouldNotify"));
            String title = (String) aiMap.getOrDefault("title", "Watchdog Alert");
            String body = (String) aiMap.getOrDefault("body", "Notification body empty");
            String severity = (String) aiMap.getOrDefault("severity", "silent");

            // Step 4: Dispatch FCM alert directly if flagged by AI
            if (shouldNotify) {
                String fcmResult = fcmService.sendAlert(title, body, severity);
                watchdogLog.setNotificationSent(fcmResult.contains("OK"));
            }

            watchdogLog.setStatus("SUCCESS");

        } catch (Exception e) {
            log.error("Watchdog execution failed for job '{}': {}", job.getName(), e.getMessage(), e);
            watchdogLog.setStatus("FAILURE");
            watchdogLog.setErrorMessage(e.getMessage());
        } finally {
            try {
                logRepository.save(watchdogLog);
            } catch (Exception dbEx) {
                log.error("Failed to save watchdog log to database: {}", dbEx.getMessage());
            }
        }
    }
}
