package com.alexsoft.smarthouse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alexsoft.smarthouse.entity.Indication;
import com.alexsoft.smarthouse.entity.Temp;
import com.alexsoft.smarthouse.repository.IndicationRepository;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class IndicationServiceTest {

    @Mock
    IndicationRepository indicationRepository;

    @Mock
    DateUtils dateUtils = new DateUtils(ZoneId.of("America/New_York"), DateTimeFormatter.ofPattern("E d, HH:mm"));

    @InjectMocks
    IndicationService indicationService;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(indicationService, "msgSavingEnabled", true);

    }

    @Test
    public void dailyAggregateTest() throws IOException {
        File file = new File(
                getClass().getClassLoader().getResource("indication-to-average-list.json").getFile());
        ObjectMapper objectMapper = new ObjectMapper();
        List<Indication> indications = objectMapper.readValue(file, new TypeReference<>() {
        });
        when(indicationRepository.findBetween(any(), any())).thenReturn(indications);

        indicationService.createAverageMeasurement(1, ChronoUnit.DAYS);
    }

    @Test
    public void dailyAggregate2Test() throws IOException {
        File file = new File(
                getClass().getClassLoader().getResource("indication-to-average-list-2.json").getFile());
        ObjectMapper objectMapper = new ObjectMapper();
        List<Indication> indications = objectMapper.readValue(file, new TypeReference<>() {});
        when(indicationRepository.findBetween(any(), any())).thenReturn(indications);
        indicationService.createAverageMeasurement(1, ChronoUnit.DAYS);
        ArgumentCaptor<List<Indication>> argumentCaptor = ArgumentCaptor.forClass(List.class);
        verify(indicationRepository).saveAll(argumentCaptor.capture());
        List<Indication> indicationList = argumentCaptor.getValue();
        assertNotNull(indicationList);
        assertThat(indicationList.size(), is(1));
        Indication indication = indicationList.get(0);
        assertThat(indication.getReceivedUtc(), is(LocalDateTime.of(2022,10,17,0,0)));
        assertThat(indication.getPublisherId(), is("ESP8266-68:C6:3A:F4:D3:49"));
        assertThat(indication.getIndicationPlace(), is("1201S-OCEAN-DR"));
        assertThat(indication.getInOut(), is(InOut.IN));
        assertThat(indication.getAggregationPeriod(), is(AggregationPeriod.DAILY));
        Temp temp = indication.getAir().getTemp();
        assertThat(temp.getCelsius(), is(12.0));
        assertThat(temp.getAh(), is(7.0));
        assertThat(temp.getRh(), is(40));
    }

}
