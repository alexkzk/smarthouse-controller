package com.alexsoft.smarthouse.watchdog.resolver;

import com.alexsoft.smarthouse.appliance.Appliance;
import com.alexsoft.smarthouse.appliance.ApplianceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DbStateResolver implements StateResolver {

    private final ApplianceService applianceService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String scheme) {
        return "db".equalsIgnoreCase(scheme);
    }

    @Override
    public String resolve(String url) {
        String path = url.replace("db://", "").trim();

        try {
            if ("appliance-metrics".equalsIgnoreCase(path)) {
                List<Appliance> appliances = applianceService.getAllAppliances();
                return objectMapper.writeValueAsString(appliances);
            }
            return "{\"error\": \"Unknown DB path: " + path + "\"}";
        } catch (Exception e) {
            throw new RuntimeException("Database state query failed: " + e.getMessage(), e);
        }
    }
}
