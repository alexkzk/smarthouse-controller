package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.internal.ApartmentDetails;
import com.alexsoft.smarthouse.core.GlobalSetting;
import com.alexsoft.smarthouse.appliance.internal.ApartmentDetailsRepository;
import com.alexsoft.smarthouse.core.GlobalSettingRepository;
import com.alexsoft.smarthouse.appliance.internal.ApartmentDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApartmentDetailsServiceTest {

    @Mock
    private ApartmentDetailsRepository apartmentDetailsRepository;

    @Mock
    private GlobalSettingRepository globalSettingRepository;

    @InjectMocks
    private ApartmentDetailsService apartmentDetailsService;

    private GlobalSetting activeIdSetting;
    private ApartmentDetails apartmentDetails;

    @BeforeEach
    public void setup() {
        activeIdSetting = new GlobalSetting();
        activeIdSetting.setKey("active_apartment_id");
        activeIdSetting.setValue("1");

        apartmentDetails = new ApartmentDetails();
        apartmentDetails.setId(1L);
        apartmentDetails.setLocationPrefix("TEST-LOC");
        apartmentDetails.setAddress("123 Test Ave");
        apartmentDetails.setLat(10.0);
        apartmentDetails.setLon(20.0);
    }

    @Test
    public void testGetLocationPrefixSuccess() {
        when(globalSettingRepository.findById("active_apartment_id")).thenReturn(Optional.of(activeIdSetting));
        when(apartmentDetailsRepository.findById(1L)).thenReturn(Optional.of(apartmentDetails));

        String prefix = apartmentDetailsService.getLocationPrefix();

        assertThat(prefix, is("TEST-LOC"));
        verify(globalSettingRepository, times(1)).findById("active_apartment_id");
        verify(apartmentDetailsRepository, times(1)).findById(1L);
        
        // Test caching, second call should not trigger repository
        String cachedPrefix = apartmentDetailsService.getLocationPrefix();
        assertThat(cachedPrefix, is("TEST-LOC"));
        verify(globalSettingRepository, times(1)).findById("active_apartment_id");
    }

    @Test
    public void testGetLocationPrefixFallback() {
        when(globalSettingRepository.findById("active_apartment_id")).thenReturn(Optional.empty());

        String prefix = apartmentDetailsService.getLocationPrefix();

        assertThat(prefix, is("935-CORKWOOD"));
    }

    @Test
    public void testGetLocationPrefixInvalidNumber() {
        activeIdSetting.setValue("invalid_number");
        when(globalSettingRepository.findById("active_apartment_id")).thenReturn(Optional.of(activeIdSetting));

        String prefix = apartmentDetailsService.getLocationPrefix();

        assertThat(prefix, is("935-CORKWOOD"));
    }

    @Test
    public void testGetCachedDetailsSuccess() {
        when(globalSettingRepository.findById("active_apartment_id")).thenReturn(Optional.of(activeIdSetting));
        when(apartmentDetailsRepository.findById(1L)).thenReturn(Optional.of(apartmentDetails));

        ApartmentDetails details = apartmentDetailsService.getCachedDetails();

        assertThat(details.getLocationPrefix(), is("TEST-LOC"));
        assertThat(details.getAddress(), is("123 Test Ave"));
    }

    @Test
    public void testGetCachedDetailsFallback() {
        when(globalSettingRepository.findById("active_apartment_id")).thenReturn(Optional.empty());

        ApartmentDetails details = apartmentDetailsService.getCachedDetails();

        assertThat(details, is(nullValue()));
    }
}
