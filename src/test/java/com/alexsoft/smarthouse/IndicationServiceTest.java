package com.alexsoft.smarthouse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.db.repository.IndicationRepository;
import com.alexsoft.smarthouse.service.IndicationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndicationServiceTest {

    @Mock
    IndicationRepository indicationRepository;

    @InjectMocks
    IndicationService indicationService;

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

}
