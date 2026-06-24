package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.ApartmentDetails;
import com.alexsoft.smarthouse.service.ApartmentDetailsService;
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
