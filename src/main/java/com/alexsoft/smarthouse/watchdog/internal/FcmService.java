package com.alexsoft.smarthouse.watchdog.internal;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    @Value("${firebase.credentials-path}")
    private String credentialsPath;

    private final FcmTokenRepository fcmTokenRepository;

    @PostConstruct
    public void initialize() {
        try {
            if (!Files.exists(Paths.get(credentialsPath))) {
                log.warn("Firebase credentials not found at {} — FCM disabled", credentialsPath);
                return;
            }
            InputStream serviceAccount = new FileInputStream(credentialsPath);
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
        }
    }

    @Transactional
    public void registerToken(String token, String deviceName) {
        fcmTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> {
                    existing.setDeviceName(deviceName);
                    existing.setRegisteredAt(LocalDateTime.now());
                    log.info("FCM token refreshed for device: {}", deviceName);
                },
                () -> {
                    fcmTokenRepository.save(FcmToken.builder()
                            .token(token)
                            .deviceName(deviceName)
                            .registeredAt(LocalDateTime.now())
                            .build());
                    log.info("FCM token registered for device: {}", deviceName);
                }
        );
    }

    @Transactional
    public String sendAlert(String title, String body) {
        return sendAlert(title, body, "severe");
    }

    @Transactional
    public String sendAlert(String title, String body, String severity) {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("Firebase not initialized, skipping FCM alert");
            return "ERROR: Firebase not initialized";
        }
        List<String> tokens = fcmTokenRepository.findAllTokens();
        if (tokens.isEmpty()) {
            log.warn("No FCM tokens registered");
            return "ERROR: No FCM tokens registered";
        }
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            try {
                Message message = Message.builder()
                        .putData("title", title)
                        .putData("body", body)
                        .putData("severity", severity != null ? severity.toLowerCase() : "severe")
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .setToken(token)
                        .build();
                String messageId = FirebaseMessaging.getInstance().send(message);
                log.info("FCM alert sent to token ...{}, messageId={}", token.substring(Math.max(0, token.length() - 10)), messageId);
                result.append("OK: sent to ...").append(token, Math.max(0, token.length() - 10), token.length())
                        .append(", messageId=").append(messageId).append("\n");
            } catch (FirebaseMessagingException e) {
                log.error("FCM send failed: {}", e.getMessage());
                result.append("ERROR: ").append(e.getMessage()).append("\n");
                if (e.getMessagingErrorCode() != null &&
                        e.getMessagingErrorCode().name().contains("UNREGISTERED")) {
                    fcmTokenRepository.deleteByToken(token);
                    log.info("Removed unregistered FCM token");
                }
            }
        }
        return result.toString().trim();
    }
}
