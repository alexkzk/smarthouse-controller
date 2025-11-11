package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import com.alexsoft.smarthouse.repository.InfluxRepository;
import com.alexsoft.smarthouse.service.IndicationServiceV2;
import com.alexsoft.smarthouse.service.IndicationServiceV3;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/indications")
@AllArgsConstructor
public class IndicationsController {

    private final IndicationServiceV2 indicationServiceV2;
    private final InfluxRepository influxRepository;
    private final IndicationServiceV3 indicationServiceV3;
    private final IndicationRepositoryV3 indicationRepositoryV3;

    @PostMapping
    public ResponseEntity<String> createIndication(@RequestBody IndicationV3 indication) {
        IndicationV3 savedIndication = indicationServiceV3.createIndication(indication);
        LocalDateTime localTime = savedIndication.getLocalTime();
        List<IndicationV3> mbs = indicationRepositoryV3.findByLocalTimeBetweenAndMeasurementType(localTime.minusMonths(1), localTime, savedIndication.getMeasurementType());
        IndicationV3 mbs1 = IndicationV3.builder().locationId("mbs").localTime(localTime).measurementType(savedIndication.getMeasurementType() + "-30d")
                .value(mbs.stream().mapToDouble(IndicationV3::getValue).sum()).build();
        indicationServiceV3.createIndication(mbs1);
        return ResponseEntity.ok(indicationServiceV3.getMbsStatus());
    }

    @PostMapping("/mbs/reprocess")
    public ResponseEntity<String> reprocessMbs() {

        // Load CSV file from resources folder
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("init_mbs.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {// Date formatters for both two-digit and four-digit years
            DateTimeFormatter twoDigitYearFormatter = DateTimeFormatter.ofPattern("dd/MM/yy");
            DateTimeFormatter fourDigitYearFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");


            // Skip the header and parse lines
            reader.lines()
                    .skip(1)  // Skip header row
                    .forEach(line -> {
                        String[] columns = line.split(",");

                        // Parse DATE to LocalDateTime
                        String dateStr = columns[0];
                        if (!dateStr.isBlank()) {
                            LocalDateTime localTime = null;

                            try {
                                // Try parsing with four-digit year
                                localTime = LocalDate.parse(dateStr, fourDigitYearFormatter).atStartOfDay();
                            } catch (DateTimeParseException e) {
                                // Fallback to two-digit year parsing
                                localTime = LocalDate.parse(dateStr, twoDigitYearFormatter).atStartOfDay();
                            }

                            // For each measurement type, create IndicationV3 if value exists
                            String[] measurementTypes = {"milato","bellion","suitan","whelmet","batrumt"};  // Column headers
                            for (int i = 2; i < columns.length; i++) {  // Start from column 2 (M onwards)
                                if (!columns[i].isBlank()) {
                                    double value = Double.parseDouble(columns[i]);

                                    String measurementType = measurementTypes[i - 2];
                                    IndicationV3 indication = IndicationV3.builder()
                                            .locationId("mbs")
                                            .localTime(localTime)
                                            .measurementType(measurementType)  // Match types
                                            .value(value)
                                            .build();

                                    // Save to database
                                    indicationServiceV3.createIndication(indication);
                                    List<IndicationV3> mbs = indicationRepositoryV3.findByLocalTimeBetweenAndMeasurementType(localTime.minusMonths(1), localTime, measurementType);
                                    IndicationV3 mbs1 = IndicationV3.builder().locationId("mbs").localTime(localTime).measurementType(measurementType + "-30d")
                                            .value(mbs.stream().mapToDouble(IndicationV3::getValue).sum()).build();
                                    indicationServiceV3.createIndication(mbs1);
                                }
                            }
                        }
                    });

            return ResponseEntity.ok("CSV data successfully parsed and inserted.");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error reading or parsing CSV file.");
        }
    }

    @GetMapping("/aggregate")
    public ResponseEntity<String> aggregate(@RequestParam String period, @RequestParam(required = false) Integer greaterThanId) {
        int aggregated = indicationServiceV2.aggregate(period, greaterThanId);
        return ResponseEntity.ok("Aggregated " + aggregated + " indications");
    }



    @PostMapping("/influx-sync")
    public ResponseEntity<String> filterByDate(@RequestParam LocalDateTime startDate, @RequestParam LocalDateTime endDate) {
        influxRepository.syncFromPostgres(startDate, endDate);
        return null;

    }

}
