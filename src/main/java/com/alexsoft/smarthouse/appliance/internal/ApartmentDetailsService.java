package com.alexsoft.smarthouse.appliance.internal;

import com.alexsoft.smarthouse.appliance.internal.ApartmentDetails;
import com.alexsoft.smarthouse.core.GlobalSetting;
import com.alexsoft.smarthouse.appliance.internal.ApartmentDetailsRepository;
import com.alexsoft.smarthouse.core.GlobalSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApartmentDetailsService {

    private final ApartmentDetailsRepository apartmentDetailsRepository;
    private final GlobalSettingRepository globalSettingRepository;

    private ApartmentDetails cachedDetails;
    private LocalDateTime cacheExpiry = LocalDateTime.MIN;

    public String getLocationPrefix() {
        if (cachedDetails == null || LocalDateTime.now().isAfter(cacheExpiry)) {
            refreshCache();
        }
        return cachedDetails != null ? cachedDetails.getLocationPrefix() : "935-CORKWOOD";
    }

    public ApartmentDetails getCachedDetails() {
        if (cachedDetails == null || LocalDateTime.now().isAfter(cacheExpiry)) {
            refreshCache();
        }
        return cachedDetails;
    }

    private synchronized void refreshCache() {
        if (LocalDateTime.now().isAfter(cacheExpiry)) {
            Optional<GlobalSetting> settingOpt = globalSettingRepository.findById("active_apartment_id");
            if (settingOpt.isPresent() && settingOpt.get().getValue() != null) {
                try {
                    Long activeId = Long.parseLong(settingOpt.get().getValue());
                    Optional<ApartmentDetails> detailsOpt = apartmentDetailsRepository.findById(activeId);
                    if (detailsOpt.isPresent()) {
                        cachedDetails = detailsOpt.get();
                        cacheExpiry = LocalDateTime.now().plusMinutes(5);
                        return;
                    }
                } catch (NumberFormatException e) {
                    // ignore and fallback
                }
            }
            cacheExpiry = LocalDateTime.now().plusMinutes(1); // retry sooner if empty or invalid
        }
    }
}
