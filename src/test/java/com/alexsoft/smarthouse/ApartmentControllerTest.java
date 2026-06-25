package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.internal.ApartmentController;
import com.alexsoft.smarthouse.appliance.internal.ApartmentDetails;
import com.alexsoft.smarthouse.appliance.internal.ApartmentDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApartmentControllerTest {

    @Mock
    private ApartmentDetailsService apartmentDetailsService;

    @InjectMocks
    private ApartmentController apartmentController;

    private ApartmentDetails apartmentDetails;

    @BeforeEach
    public void setup() {
        apartmentDetails = new ApartmentDetails();
        apartmentDetails.setId(1L);
        apartmentDetails.setLocationPrefix("TEST-LOC");
        apartmentDetails.setAddress("123 Test Ave");
    }

    @Test
    public void testGetActiveApartmentSuccess() {
        when(apartmentDetailsService.getCachedDetails()).thenReturn(apartmentDetails);

        ResponseEntity<ApartmentDetails> response = apartmentController.getActiveApartment();

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), is(apartmentDetails));
        assertThat(response.getBody().getAddress(), is("123 Test Ave"));
    }

    @Test
    public void testGetActiveApartmentNotFound() {
        when(apartmentDetailsService.getCachedDetails()).thenReturn(null);

        ResponseEntity<ApartmentDetails> response = apartmentController.getActiveApartment();

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
        assertThat(response.getBody(), is(nullValue()));
    }
}
