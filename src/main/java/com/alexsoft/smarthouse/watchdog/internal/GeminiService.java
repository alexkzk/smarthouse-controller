package com.alexsoft.smarthouse.watchdog.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public String evaluateState(String prompt, String gatheredState) {
        if (apiKey == null || apiKey.isBlank()) {
            // Attempt to read from environment variable if Spring property is empty
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key (GEMINI_API_KEY) is not configured");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

        try {
            // Build the prompt by appending gathered state
            String fullPrompt = prompt + "\n\nCurrent System State JSON:\n" + gatheredState;

            ObjectNode textNode = objectMapper.createObjectNode().put("text", fullPrompt);
            ObjectNode partsNode = objectMapper.createObjectNode();
            partsNode.set("parts", objectMapper.createArrayNode().add(textNode));
            
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.set("contents", objectMapper.createArrayNode().add(partsNode));

            // Request structured JSON schema output
            ObjectNode responseMimeType = objectMapper.createObjectNode().put("responseMimeType", "application/json");
            contentNode.set("generationConfig", responseMimeType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(contentNode.toString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            String rawBody = response.getBody();
            if (rawBody == null) {
                throw new RuntimeException("Empty response body received from Gemini API");
            }

            return extractTextFromGeminiResponse(rawBody);

        } catch (Exception e) {
            log.error("Failed to query Gemini API: {}", e.getMessage());
            throw new RuntimeException("Gemini API Error: " + e.getMessage(), e);
        }
    }

    private String extractTextFromGeminiResponse(String rawJson) throws Exception {
        var root = objectMapper.readTree(rawJson);
        return root.path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();
    }
}
