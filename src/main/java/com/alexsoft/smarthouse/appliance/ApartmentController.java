package com.alexsoft.smarthouse.appliance;

import com.alexsoft.smarthouse.appliance.ApartmentDetails;
import com.alexsoft.smarthouse.appliance.ApartmentDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apartment")
@RequiredArgsConstructor
public class ApartmentController {

    private final ApartmentDetailsService apartmentDetailsService;

    @GetMapping("/active")
    public ResponseEntity<ApartmentDetails> getActiveApartment() {
        ApartmentDetails details = apartmentDetailsService.getCachedDetails();
        if (details != null) {
            return ResponseEntity.ok(details);
        }
        return ResponseEntity.notFound().build();
    }
}
