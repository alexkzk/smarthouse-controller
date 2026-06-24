package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.ApartmentDetails;
import com.alexsoft.smarthouse.repository.ApartmentDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApartmentDetailsService {

    private final ApartmentDetailsRepository repository;

    private ApartmentDetails cachedDetails;
    private LocalDateTime cacheExpiry = LocalDateTime.MIN;

    public String getLocationPrefix() {
        if (LocalDateTime.now().isAfter(cacheExpiry)) {
            refreshCache();
        }
        return cachedDetails != null ? cachedDetails.getLocationPrefix() : "935-CORKWOOD";
    }

    private synchronized void refreshCache() {
        if (LocalDateTime.now().isAfter(cacheExpiry)) {
            Optional<ApartmentDetails> detailsOpt = repository.findAll().stream().findFirst();
            if (detailsOpt.isPresent()) {
                cachedDetails = detailsOpt.get();
                cacheExpiry = LocalDateTime.now().plusMinutes(5);
            } else {
                cacheExpiry = LocalDateTime.now().plusMinutes(1); // retry sooner if empty
            }
        }
    }
}
