package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV2;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV2;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IndicationServiceV2 {

    private final IndicationRepositoryV2 indicationRepositoryV2;

    public int aggregate(String period, Integer greaterThanId) {

        List<IndicationV2> all = retrieveDataForPeriod(period, greaterThanId);
        LocalDateTime aggregationTime = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        all.forEach(i -> i.setAggregationTimeUtc(aggregationTime));

        indicationRepositoryV2.saveAll(all);

        List<IndicationV2> aggregatedData = all.stream()
                .collect(Collectors.groupingBy(
                        indication -> new IndicationKey(
                                indication.getIndicationPlace(),
                                aggregateTime(indication.getLocalTime(), period),
                                indication.getInOut()
                        ),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> createAggregatedIndication(list, period)
                        )
                )).values().stream()
                .sorted(Comparator.comparing(IndicationV2::getLocalTime)
                        .thenComparing(IndicationV2::getIndicationPlace)
                        .thenComparing(IndicationV2::getInOut))
                .toList();

        indicationRepositoryV2.saveAll(new ArrayList<>(aggregatedData));
        return aggregatedData.size();
    }

    private LocalDateTime aggregateTime(LocalDateTime time, String period) {
        return switch (period.toUpperCase()) {
            case "HOURLY" -> time.truncatedTo(ChronoUnit.HOURS);
            case "DAILY" -> time.truncatedTo(ChronoUnit.DAYS);
            case "MONTHLY" -> time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case "YEARLY" -> time.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            default -> throw new IllegalArgumentException("Invalid aggregation period: " + period);
        };
    }

    private List<IndicationV2> retrieveDataForPeriod(String period, Integer greaterThanId) {
        return switch (period.toUpperCase()) {
            case "HOURLY" -> (greaterThanId != null) ?
                    indicationRepositoryV2.findByIdGreaterThan(greaterThanId) :
                    indicationRepositoryV2.findAll();
            case "DAILY" -> indicationRepositoryV2.findByAggregationPeriod("HOURLY");
            case "MONTHLY" -> indicationRepositoryV2.findByAggregationPeriod("DAILY");
            case "YEARLY" -> indicationRepositoryV2.findByAggregationPeriod("MONTHLY");
            default -> throw new IllegalArgumentException("Invalid aggregation period: " + period);
        };
    }

    private IndicationV2 createAggregatedIndication(List<IndicationV2> list, String period) {
        Set<Integer> distinctTimeUnits = new HashSet<>();
        ChronoField unit = switch (period.toUpperCase()) {
            case "HOURLY" -> ChronoField.MINUTE_OF_HOUR;
            case "DAILY" -> ChronoField.HOUR_OF_DAY;
            case "MONTHLY" -> ChronoField.DAY_OF_MONTH;
            case "YEARLY" -> ChronoField.MONTH_OF_YEAR;
            default -> throw new IllegalArgumentException("Unsupported period: " + period);
        };

        list.stream().filter(i -> i.getTemperature().getValue() != null)
                .forEach(i -> distinctTimeUnits.add(i.getLocalTime().get(unit)));

        IndicationV2 indicationV2 = list.get(0);
        IndicationV2 aggregatedIndication = new IndicationV2()
                .setIndicationPlace(indicationV2.getIndicationPlace())
                .setLocalTime(aggregateTime(indicationV2.getLocalTime(), period))
                .setInOut(indicationV2.getInOut())
                .setAggregationAccuracy(calculateAccuracy(distinctTimeUnits.size(), unit, period))
                .setAggregationPeriod(period.toUpperCase());

        createMetar(indicationV2, list, aggregatedIndication);
        aggregateMeasurements(list, aggregatedIndication);

        return aggregatedIndication;
    }

    private Integer calculateAccuracy(int size, ChronoField unit, String period) {
        if (unit == ChronoField.MINUTE_OF_DAY) {
            return Math.round(size / 60f * 100);
        } else if (unit == ChronoField.HOUR_OF_DAY) {
            return Math.round(size / 24f * 100);
        } else if (unit == ChronoField.DAY_OF_MONTH) {
            return Math.round(size / ((365 * 3 + 366) / 4f / 12) * 100);
        } else if (unit == ChronoField.MONTH_OF_YEAR) {
            return Math.round(size / 12f * 100);
        } else {
            return null;
        }
    }

    private void createMetar(IndicationV2 indicationV2, List<IndicationV2> list, IndicationV2 aggregatedIndication) {
        int firstId = indicationV2.getId();
        int lastId = list.get(list.size() - 1).getId();
        aggregatedIndication.setMetar(String.format("aggregation_period = '%s' AND in_out = '%s' AND indication_place = '%s' AND id >= %d AND id <= %d",
                indicationV2.getAggregationPeriod(), indicationV2.getInOut(), indicationV2.getIndicationPlace(), firstId, lastId));
    }

    private void aggregateMeasurements(List<IndicationV2> list, IndicationV2 aggregatedIndication) {
        List<IndicationV2> temperatures = list.stream().filter(i -> i.getTemperature().getValue() != null).toList();
        aggregatedIndication.getTemperature().setValue(temperatures.stream().mapToDouble(i -> i.getTemperature().getValue()).average().orElse(-100.0));
        aggregatedIndication.getTemperature().setMax(temperatures.stream().mapToDouble(i -> i.getTemperature().getValue()).max().orElse(-100.0));
        aggregatedIndication.getTemperature().setMin(temperatures.stream().mapToDouble(i -> i.getTemperature().getValue()).min().orElse(-100.0));

        List<IndicationV2> humidities = list.stream().filter(i -> i.getRelativeHumidity().getValue() != null).toList();
        aggregatedIndication.getRelativeHumidity().setValue(humidities.stream().mapToDouble(i -> i.getRelativeHumidity().getValue()).average().orElse(-100));
        aggregatedIndication.getRelativeHumidity().setMax(humidities.stream().mapToDouble(i -> i.getRelativeHumidity().getValue()).max().orElse(-100));
        aggregatedIndication.getRelativeHumidity().setMin(humidities.stream().mapToDouble(i -> i.getRelativeHumidity().getValue()).min().orElse(-100));

        List<IndicationV2> absoluteHumidities = list.stream().filter(i -> i.getAbsoluteHumidity().getValue() != null).toList();
        aggregatedIndication.getAbsoluteHumidity().setValue(absoluteHumidities.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).average().orElse(-100));
        aggregatedIndication.getAbsoluteHumidity().setMax(absoluteHumidities.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).max().orElse(-100));
        aggregatedIndication.getAbsoluteHumidity().setMin(absoluteHumidities.stream().mapToDouble(i -> i.getAbsoluteHumidity().getValue()).min().orElse(-100));

        List<IndicationV2> pressures = list.stream().filter(i -> i.getPressure().getValue() != null).toList();
        aggregatedIndication.getPressure().setValue(pressures.stream().mapToDouble(i -> i.getPressure().getValue()).average().orElse(-100.0));
        aggregatedIndication.getPressure().setMin(pressures.stream().mapToDouble(i -> i.getPressure().getValue()).min().orElse(-100.0));
        aggregatedIndication.getPressure().setMax(pressures.stream().mapToDouble(i -> i.getPressure().getValue()).max().orElse(-100.0));
    }

    private record IndicationKey(String indicationPlace, LocalDateTime localTime, String inOut) {
    }
}
