package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.configuration.SmarthouseConfiguration;
import com.alexsoft.smarthouse.db.entity.Air;
import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.db.entity.Pressure;
import com.alexsoft.smarthouse.db.entity.Quality;
import com.alexsoft.smarthouse.db.entity.Temp;
import com.alexsoft.smarthouse.db.entity.Wind;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.TempUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.alexsoft.smarthouse.utils.Constants.*;
import static com.alexsoft.smarthouse.utils.MathUtils.*;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class IndicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndicationService.class);

    public static final String IN_PREFIX = "IN-";
    public static final String OUT_PREFIX = "OUT-";
    public static final String DATE = "Date";
    public static final Comparator<Object> OBJECT_TO_STRING_COMPARATOR = Comparator.comparing(o -> ((String) o));
    public static final String PM_10 = "PM10 ";
    public static final String PM_2_5 = "PM2.5 ";
    public static final String IAQ = "IAQ ";
    public static final String SIAQ = "SIAQ ";
    public static final String GR = "GR ";
    public static final String OUTSIDE_STATUS_PATTERN = "%s - %s";
    public static final String OUTSIDE_STATUS_PATTERN2 = "C[%s]SM %d/%d/%d[°C] %d/%d/%d[AH]";
    public static final String TEMP_AND_AH_PATTERN = "%s %s°C/%s";

    private final SmarthouseConfiguration smarthouseConfiguration;
    private final HouseStateRepository houseStateRepository;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

    public List<Indication> aggregateOnInterval(
            Integer aggregationIntervalMinutes, Integer minutes, Integer hours, Integer days
    ) {
        LOGGER.debug("Aggregating houseStates on {} min interval, requested period: {} days, {} hours, {} minutes",
                aggregationIntervalMinutes, days, hours, minutes);

        LocalDateTime interval = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days));

        return aggregateOnInterval(aggregationIntervalMinutes, interval, LocalDateTime.now());
    }

    public void aggregateOnInterval(Integer interval, AggregationPeriod aggregationPeriod) {
        LocalDateTime endDate = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        LocalDateTime startDate = endDate
                .minusHours(aggregationPeriod == AggregationPeriod.HOURLY ? interval : 0)
                .minusMinutes(aggregationPeriod == AggregationPeriod.MINUTELY ? interval : 0)
                .withSecond(0).withNano(0);
        List<Indication> indications = aggregateOnInterval(interval, startDate, endDate);
        indications.forEach(ind -> {
            ind.setIssued(endDate);
            ind.setAggregationPeriod(aggregationPeriod);
        });
        List<Indication> savedIndications = houseStateRepository.saveAll(indications);
        String s = null;
        try {
            s = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(savedIndications);
        } catch (JsonProcessingException e) {
            LOGGER.warn(e.getMessage(), e);
        }
        LOGGER.info("Saved aggregated measurements for the following interval: {} - {}. Aggregation period: {} {}.\n{}",
                startDate, endDate, interval, aggregationPeriod, s);
    }

    public List<Indication> aggregateOnInterval(
            Integer aggregationIntervalMinutes, LocalDateTime startDate, LocalDateTime endDate
    ) {

        long startMillis = System.currentTimeMillis();
        List<Indication> fetchedHouseStates = houseStateRepository.findAfter(startDate, endDate);

        if (aggregationIntervalMinutes != null && 0 != aggregationIntervalMinutes) {
            long aggregationStart = System.currentTimeMillis();
            List<Indication> indications = fetchedHouseStates.stream().collect(Collectors.groupingBy(ind -> ind.getIndicationPlace() + "&" + ind.getInOut()))
                    .entrySet().stream().flatMap(
                            entry -> aggregateByInterval(
                                    aggregationIntervalMinutes,
                                    entry.getValue(),
                                    entry.getKey().split("&")[0],
                                    InOut.valueOf(entry.getKey().split("&")[1])
                            ).stream()).sorted().collect(Collectors.toList());

            LOGGER.debug("Aggregating completed, aggregation time: {} ms, total: {} ms", System.currentTimeMillis() - aggregationStart,
                    System.currentTimeMillis() - startMillis);

            return indications;
        } else {
            return fetchedHouseStates.stream().sorted().collect(toList());
        }
    }

    private List<Indication> aggregateByInterval(Integer aggregationIntervalMinutes, List<Indication> fetchedHouseStates, String indicationPlace, InOut inOut) {
        return fetchedHouseStates.stream().collect(
                        Collectors.groupingBy(
                                houseState -> dateUtils.roundDateTime(houseState.getReceived(), aggregationIntervalMinutes),
                                TreeMap::new,
                                Collectors.collectingAndThen(toList(), indications -> averageList(indications, indicationPlace, inOut))
                        )
                ).entrySet().stream().peek(el -> el.getValue().setReceived(el.getKey()))
                .map(Map.Entry::getValue).sorted().collect(toList());
    }

    private Indication averageList(List<Indication> indications, String indicationPlace, InOut inOut) {

        Indication averagedIndication = new Indication();
        Set<String> places = indications.stream().map(Indication::getIndicationPlace).collect(Collectors.toSet());
        Set<String> publisherIds = indications.stream().map(Indication::getPublisherId).collect(Collectors.toSet());
        if (places.size() != 1 || publisherIds.size() != 1) {
            throw new RuntimeException();
        }
        averagedIndication.setIndicationPlace(indicationPlace);
        averagedIndication.setPublisherId(new ArrayList<>(publisherIds).get(0));
        averagedIndication.setInOut(inOut);

        List<Quality> aqis = indications.stream().map(indication -> indication.getAir().getQuality()).filter(Objects::nonNull).collect(Collectors.toList());
        Quality quality = Quality.builder()
                .pm25(aqis.stream().map(Quality::getPm25).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .pm10(aqis.stream().map(Quality::getPm10).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .iaq(doubleToInt(aqis.stream().map(Quality::getIaq).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN)))
                .build();

        List<Temp> temps = indications.stream().map(indication -> indication.getAir().getTemp()).filter(Objects::nonNull).collect(Collectors.toList());
        double averageRh = temps.stream().filter(temp -> temp.getRh() != null).mapToInt(Temp::getRh).average().orElse(Double.NaN);
        Temp avgTemp = Temp.builder()
                .celsius(temps.stream().filter(temp -> temp.getCelsius() != null).mapToDouble(Temp::getCelsius).average().orElse(Double.NaN))
                .ah(temps.stream().filter(temp -> temp.getAh() != null).mapToDouble(Temp::getAh).average().orElse(Double.NaN))
                .rh(doubleToInt(averageRh))
                .build();

        List<Pressure> pressures = indications.stream().map(indication -> indication.getAir().getPressure()).filter(Objects::nonNull).collect(Collectors.toList());
        Pressure avgPressure = Pressure.builder()
                .mmHg(pressures.stream().filter(pr -> pr.getMmHg() != null).mapToDouble(Pressure::getMmHg).average().orElse(Double.NaN)).build();

        List<Wind> winds = indications.stream().map(houseState -> houseState.getAir().getWind()).filter(Objects::nonNull).collect(Collectors.toList());
        Wind averagedWind = Wind.builder()
                .direction(doubleToInt(winds.stream().filter(wind -> wind.getDirection() != null).mapToDouble(Wind::getDirection).average().orElse(Double.NaN)))
                .speedMs(doubleToInt(winds.stream().filter(winf -> winf.getSpeedMs() != null).mapToDouble(Wind::getDirection).average().orElse(Double.NaN)))
                .build();

        averagedIndication.setAir(Air.builder()
                .quality(quality.isEmpty() ? null : quality)
                .temp(avgTemp.isEmpty() ? null : avgTemp)
                .pressure(avgPressure.isEmpty() ? null : avgPressure)
                .wind(averagedWind.isEmpty() ? null : averagedWind)
                .build());

        return averagedIndication;

    }

    public void save(Indication indicationToSave) {

        indicationToSave.setAggregationPeriod(AggregationPeriod.INSTANT);
        normalizeTempAndHumidValues(indicationToSave);
        calculateAbsoluteHumidity(indicationToSave);
        setInOut(indicationToSave);
        setRecievedDateTime(indicationToSave);
        resetTempAndHumidForPlace("TERRACE", indicationToSave);
        setEmptyMeasurementsToNull(indicationToSave);
        convertPressureToMmHg(indicationToSave);

        if (msgSavingEnabled) {
            Indication savedIndication = houseStateRepository.saveAndFlush(indicationToSave);
            LOGGER.debug("Saved indication {}", savedIndication);
        } else {
            LOGGER.debug("Skipping of saving indication {}", indicationToSave);
        }
    }

    private boolean hasNoAhCalculated(Indication indication) {
        return indication.getAir() != null && indication.getAir().getTemp() != null &&
                indication.getAir().getTemp().getCelsius() != null && indication.getAir().getTemp().getRh() != null &&
                indication.getAir().getTemp().getAh() == null;
    }

    public List<Indication> findHourly() {
        return houseStateRepository.findAfter(dateUtils.getInterval(15, 0, 0, true), dateUtils.getInterval(0, 0, 0, true));
    }

    public String getHourlyAveragedShortStatus() {

        List<Indication> hourlyAverage = findHourly().stream().filter(hst -> hst.getInOut() == InOut.OUT).collect(toList());

        List<Indication> northMeasurements = filterByPlace(hourlyAverage, NORTH_MEASURE_PLACE);
        List<Indication> southMeasurements = filterByPlace(hourlyAverage, SOUTH_MEASURE_PLACE);
        List<Indication> seattleMeasurements = filterByPlace(hourlyAverage, SEATTLE_MEASURE_PLACE);
        List<Indication> miamiMeasurements = filterByPlace(hourlyAverage, MIAMI_MEASURE_PLACE);
        List<Indication> uklnMeasurements = filterByPlace(hourlyAverage, UKLN_MEASURE_PLACE);

        Long southTemp = calculateAverageTemperature(southMeasurements);
        Long northTemp = calculateAverageTemperature(northMeasurements);
        Long northAh = calculateAverageAh(northMeasurements);
        Long southAh = calculateAverageAh(southMeasurements);

        Long seattleTemp = null;
        Long seattleAh = null;
        if (!CollectionUtils.isEmpty(seattleMeasurements)) {
            seattleTemp = calculateAverageTemperature(seattleMeasurements);
            seattleAh = calculateAverageAh(seattleMeasurements);
        }

        Long miamiTemp = null;
        Long miamiAh = null;
        if (!CollectionUtils.isEmpty(miamiMeasurements)) {
            miamiTemp = calculateAverageTemperature(miamiMeasurements);
            miamiAh = calculateAverageAh(miamiMeasurements);
        }

        boolean actualMeasuresNorth = southTemp == null || (southTemp == null && northTemp == null) || northTemp < southTemp;

        return String.format(OUTSIDE_STATUS_PATTERN2,
                actualMeasuresNorth ? "N" : "S",
                actualMeasuresNorth ? northTemp : southTemp,
                seattleTemp,
                miamiTemp,
                actualMeasuresNorth ? northAh : southAh,
                seattleAh,
                miamiAh
                );
    }

    private List<Indication> filterByPlace(List<Indication> hourlyAverage, String measurePlace) {
        return hourlyAverage.stream().filter(hst -> hst.getIndicationPlace().equalsIgnoreCase(measurePlace)).collect(toList());
    }
    
    public Long calculateAverageTemperature(List<Indication> indications) {
        return round(indications.stream().filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getCelsius() != null).mapToDouble(hst -> hst.getAir().getTemp().getCelsius()).average().orElse(Double.NaN));
    }

    public Long calculateAverageAh(List<Indication> indications) {
        return round(indications.stream().filter(hst -> hst.getAir().getTemp() != null && hst.getAir().getTemp().getAh() != null).mapToDouble(hst -> hst.getAir().getTemp().getAh()).average().orElse(Double.NaN));
    }

    public List<Indication> findAll() {
        return houseStateRepository.findAll();
    }

    public ChartDto getAggregatedData() {
        List<Map<String, Object>> aggregates = houseStateRepository.aggregate();
        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }

    private void setColors(ChartDto chartDto) {
        Object[] aqiColors = new Object[]{"#ff0000", "#791d00", "#e27f67", "#969eff", "#4254f5"};
        chartDto.setAqiColors(aqiColors);

        Object[] outdoorTemp = (Object[]) chartDto.getOutdoorTemps()[0];
        List<Object> outdoorTempHeader = Arrays.stream(outdoorTemp).collect(toList());
        outdoorTempHeader = outdoorTempHeader.subList(1, outdoorTempHeader.size());
        Object[] outdoorColors = outdoorTempHeader.stream().map(
                s -> smarthouseConfiguration.getColors().get(s) == null ? "black" : smarthouseConfiguration.getColors().get(s)
        ).toArray();
        chartDto.setOutdoorColors(outdoorColors);

        Object[] rhs = (Object[]) chartDto.getRhs()[0];
        List<Object> rhHeader = Arrays.stream(rhs).collect(toList());
        rhHeader = rhHeader.subList(1, rhHeader.size());
        Object[] rhColors = rhHeader.stream().map(
                s -> smarthouseConfiguration.getColors().get(s) == null ? "black" : smarthouseConfiguration.getColors().get(s)
        ).toArray();
        chartDto.setRhsColors(rhColors);

        Object[] ahs = (Object[]) chartDto.getAhs()[0];
        List<Object> ahHeader = Arrays.stream(ahs).collect(toList());
        ahHeader = ahHeader.subList(1, ahHeader.size());
        Object[] ahColors = ahHeader.stream().map(
                s -> smarthouseConfiguration.getColors().get(s) == null ? "black" : smarthouseConfiguration.getColors().get(s)
        ).toArray();
        chartDto.setAhsColors(ahColors);

        Object[] indoor = (Object[]) chartDto.getIndoorTemps()[0];
        List<Object> indoorHeader = Arrays.stream(indoor).collect(toList());
        indoorHeader = indoorHeader.subList(1, indoorHeader.size());
        Object[] indoorColors = indoorHeader.stream().map(
                s -> smarthouseConfiguration.getColors().get(s) == null ? "black" : smarthouseConfiguration.getColors().get(s)
        ).toArray();
        chartDto.setIndoorColors(indoorColors);

    }

    private void setAqis(final List<Map<String, Object>> aggregates, final ChartDto chartDto) {

        Map<Timestamp, Object[]> aqiValuesMap = new TreeMap<>();
        List<Map<String, Object>> aqis = aggregates.stream().filter(map -> map.get("pm10") != null ||
            map.get("pm25") != null || map.get("iaq") != null).collect(toList());
        List<Object> aqiHeader = new ArrayList<>();
        aqiHeader.add(DATE);
        aqiHeader.addAll(aqis.stream().map(m -> m.get("indication_place")).distinct().map(place ->
            List.of(PM_10 + place, PM_2_5 + place, IAQ + place, SIAQ + place, GR + place)).flatMap(Collection::stream).collect(toList()));
        aqiHeader.sort(OBJECT_TO_STRING_COMPARATOR);

        Set<String> placesAdded = new LinkedHashSet<>();
        for (Map<String, Object> map : aqis) {
            Object pm10 = map.get("pm10");
            Object pm25 = map.get("pm25");
            Object iaq = map.get("iaq");
            Object siaq = map.get("siaq");
            Object gr = map.get("gas_resistance");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = aqiValuesMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[aqiHeader.size()]);
            if (pm10 != null) {
                String currPlace = PM_10 + place;
                placesAdded.add(currPlace);
                valueArr[aqiHeader.indexOf(currPlace)] = ((BigDecimal) pm10).doubleValue();
            }
            if (pm25 != null) {
                String currPlace = PM_2_5 + place;
                placesAdded.add(currPlace);
                valueArr[aqiHeader.indexOf(currPlace)] = ((BigDecimal) pm25).doubleValue();
            }
            if (gr != null) {
                String currPlace = GR + place;
                placesAdded.add(currPlace);
                valueArr[aqiHeader.indexOf(currPlace)] = ((BigDecimal) gr).doubleValue();
            }
            if (iaq != null) {
                String currPlace = IAQ + place;
                placesAdded.add(currPlace);
                valueArr[aqiHeader.indexOf(currPlace)] = ((BigDecimal) iaq).doubleValue();
            }
            if (siaq != null) {
                String currPlace = SIAQ + place;
                placesAdded.add(currPlace);
                valueArr[aqiHeader.indexOf(currPlace)] = ((BigDecimal) siaq).doubleValue();
            }
            valueArr[aqiHeader.indexOf(DATE)] = dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
        }

        List<String> placesAddedList = new ArrayList<>(placesAdded);
        Collection<Object[]> aqisFinal = aqiValuesMap.values().stream().map(arr -> {
                Object[] newArr = new Object[placesAddedList.size() + 1];
                newArr[0] = arr[0];
                for (int i = 0; i < placesAddedList.size(); i++) {
                    newArr[i + 1] = arr[aqiHeader.indexOf(placesAddedList.get(i))];
                }
                return newArr;
            }
        ).collect(toList());
        List<Object[]> aqiList = new ArrayList<>(aqisFinal.size()+1);
        placesAddedList.add(0, DATE);
        if(CollectionUtils.isNotEmpty(aqisFinal)) {
            aqiList.add(placesAddedList.toArray());
        }
        aqiList.addAll(aqisFinal);
        chartDto.setAqi(!aqiList.isEmpty() ? aqiList.toArray() : getEmptyDataArray());
    }

    private void setAhs(final List<Map<String, Object>> aggregates, final ChartDto chartDto) {
        Map<Timestamp, Object[]> ahsMap = new TreeMap<>();
        List<Map<String, Object>> ahs = aggregates.stream().filter(map -> map.get("ah") != null)
                .collect(toList());
        List<Object> ahsHeader = new ArrayList<>();
        ahsHeader.add(DATE);
        for (Map<String, Object> map : ahs) {
            String place = (String) map.get("indication_place");
            if (!ahsHeader.contains(place)) {
                ahsHeader.add(place);
            }
        }
        ahsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : ahs) {
            Object dbAh = map.get("ah");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = ahsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[ahsHeader.size()]);
            valueArr[ahsHeader.indexOf(place)] = ((BigDecimal) dbAh).doubleValue();
            valueArr[ahsHeader.indexOf(DATE)] = dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
        }
        Collection<Object[]> ahValues = ahsMap.values();
        List<Object[]> ahList = new ArrayList<>(ahValues.size()+1);
        if(CollectionUtils.isNotEmpty(ahValues)) {
            ahList.add(ahsHeader.toArray());
        }
        ahList.addAll(ahValues);
        chartDto.setAhs(!ahList.isEmpty() ? ahList.toArray() : getEmptyDataArray());
    }

    private void setRhs(final List<Map<String, Object>> aggregates, final ChartDto chartDto) {
        Map<Timestamp, Object[]> rhsMap = new TreeMap<>();
        List<Map<String, Object>> rhs = aggregates.stream().filter(map -> map.get("rh") != null)
                .collect(toList());
        List<Object> rhsHeader = new ArrayList<>();
        rhsHeader.add(DATE);
        for (Map<String, Object> map : rhs) {
            String place = (String) map.get("indication_place");
            if (!rhsHeader.contains(place)) {
                rhsHeader.add(place);
            }
        }
        rhsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : rhs) {
            Object dbRh = map.get("rh");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = rhsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[rhsHeader.size()]);
            valueArr[rhsHeader.indexOf(place)] = ((BigDecimal) dbRh).doubleValue();
            valueArr[rhsHeader.indexOf(DATE)] = dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
        }
        Collection<Object[]> rhValues = rhsMap.values();
        List<Object[]> rhList = new ArrayList<>(rhValues.size()+1);
        if(CollectionUtils.isNotEmpty(rhValues)){
            rhList.add(rhsHeader.toArray());
        }
        rhList.addAll(rhValues);
        chartDto.setRhs(!rhList.isEmpty() ? rhList.toArray() : getEmptyDataArray());
    }

    private void setTemps(final List<Map<String, Object>> aggregates, final ChartDto chartDto) {
        Map<Timestamp, Object[]> inTempsMap = new TreeMap<>();
        List<Map<String, Object>> inTemp = aggregates.stream()
                .filter(map -> (map.get("in_out") != null && map.get("in_out").equals("IN")) && map.get("temp") != null)
            .collect(toList());
        List<Object> inTempHeader = new ArrayList<>();
        inTempHeader.add(DATE);
        for (Map<String, Object> map : inTemp) {
            String place = (String) map.get("indication_place");
            if (!inTempHeader.contains(place)) {
                inTempHeader.add(place);
            }
        }
        inTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : inTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = inTempsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[inTempHeader.size()]);
            valueArr[inTempHeader.indexOf(place)] = ((BigDecimal) dbTemp).doubleValue();
            valueArr[inTempHeader.indexOf(DATE)] = dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
        }
        Collection<Object[]> inTempsValues = inTempsMap.values();
        List<Object[]> inTempsList = new ArrayList<>(inTempsValues.size()+1);
        if (CollectionUtils.isNotEmpty(inTempsValues)) {
            inTempsList.add(inTempHeader.toArray());
        }
        inTempsList.addAll(inTempsValues);
        chartDto.setIndoorTemps(!inTempsList.isEmpty() ? inTempsList.toArray() : getEmptyDataArray());

        Map<Timestamp, Object[]> outTempsMap = new TreeMap<>();
        List<Map<String, Object>> outTemp = aggregates.stream().filter(map -> (map.get("in_out") != null && map.get("in_out").equals("OUT")) && map.get("temp") != null)
                .collect(toList());
        List<Object> outTempHeader = new ArrayList<>();
        outTempHeader.add(DATE);
        for (Map<String, Object> map : outTemp) {
            String place = (String) map.get("indication_place");
            if (!outTempHeader.contains(place)) {
                outTempHeader.add(place);
            }
        }
        outTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : outTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = outTempsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[outTempHeader.size()]);
            valueArr[outTempHeader.indexOf(place)] = ((BigDecimal) dbTemp).doubleValue();
            valueArr[outTempHeader.indexOf(DATE)] = dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
        }
        Collection<Object[]> outTempValues = outTempsMap.values();
        List<Object[]> outTempList = new ArrayList<>(outTempsMap.size()+1);
        if (CollectionUtils.isNotEmpty(outTempValues)) {
            outTempList.add(outTempHeader.toArray());
        }
        outTempList.addAll(outTempValues);
        chartDto.setOutdoorTemps(!outTempList.isEmpty() ? outTempList.toArray() : getEmptyDataArray());
    }

    private Object[][] getEmptyDataArray() {
        return new Object[][] {{DATE, "NO DATA"}, {dateUtils.localDateTimeToString(LocalDateTime.now()), 0D}};
    }

    private void convertPressureToMmHg(Indication indication) {
        if (indication.getAir().getPressure() != null && indication.getAir().getPressure().isEmpty() && indication.getAir().getPressure() != null) {
            // Convert to mm Rh
            indication.getAir().getPressure().setMmHg(indication.getAir().getPressure().getMmHg() * 0.00750062);
        }
    }

    private void setEmptyMeasurementsToNull(Indication indication) {
        if (indication.getAir().getTemp() != null && indication.getAir().getTemp().isEmpty()) {
            LOGGER.debug("Indication's Temp is empty, setting it as NULL\n{}", indication);
            indication.getAir().setTemp(null);
        }

        if (indication.getAir().getQuality() != null && indication.getAir().getQuality().isEmpty()) {
            LOGGER.debug("Indication's Quality is empty, setting it as NULL\n{}", indication);
            indication.getAir().setQuality(null);
        }

        if (indication.getAir().getPressure() != null && indication.getAir().getPressure().isEmpty()) {
            LOGGER.debug("Indication's Pressure is empty, setting it as NULL\n{}", indication);
            indication.getAir().setPressure(null);
        }
    }

    private void resetTempAndHumidForPlace(String place, Indication indication) {
        if (indication.getIndicationPlace().equals(place) && indication.getInOut() == InOut.OUT) {
            // TODO temporary disabled due to sensor malfunction
            indication.getAir().getTemp().setAh(null);
            indication.getAir().getTemp().setRh(null);
        }
    }

    private void setRecievedDateTime(Indication indication) {
        indication.setReceived(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
    }

    private void setInOut(Indication indication) {
        //  todo temporary, remove after changing the msg format on publishers
        if (indication.getIndicationPlace().startsWith(IN_PREFIX)) {
            indication.setInOut(InOut.IN);
            indication.setIndicationPlace(indication.getIndicationPlace().replace(IN_PREFIX, ""));
        } else if (indication.getIndicationPlace().startsWith(OUT_PREFIX)) {
            indication.setInOut(InOut.OUT);
            indication.setIndicationPlace(indication.getIndicationPlace().replace(OUT_PREFIX, ""));
        }
    }

    private void calculateAbsoluteHumidity(Indication indication) {
        if (hasNoAhCalculated(indication)) {
            Float aH = tempUtils.calculateAbsoluteHumidity(
                    indication.getAir().getTemp().getCelsius().floatValue(),
                    indication.getAir().getTemp().getRh()
            );
            indication.getAir().getTemp().setAh(aH.doubleValue());
        }
    }

    private void normalizeTempAndHumidValues(Indication indication) {
        if (indication.getAir() != null && indication.getAir().getTemp() != null) {
            if (indication.getAir().getTemp().normalize()) {
                LOGGER.warn("Out of range temp measurements observed");
            }
        }
    }

    public List<Indication> findWithinInterval(final Integer minutes, final Integer hours, final Integer days) {
        return houseStateRepository.findAfter(dateUtils.getInterval(minutes, hours, days, true), dateUtils.getInterval(0, 0, 0, true));
    }

    private Long calculateAveragePm25(List<Indication> indications) {
        return round(indications.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getPm25() != null).mapToDouble(hst -> hst.getAir().getQuality().getPm25()).average().orElse(Double.NaN));
    }

    public Long calculateAverageIaq(List<Indication> indications) {
        return round(indications.stream().filter(hst -> hst.getAir().getQuality() != null && hst.getAir().getQuality().getIaq() != null).mapToInt(hst -> hst.getAir().getQuality().getIaq()).average().orElse(Double.NaN));
    }

    private String toAirQualityString(Long avgIaq, Long avgPm25) {
        return String.format("IAQ %s/%s", measureToString(avgIaq), measureToString(avgPm25));
    }

    private String toTempAndAhString(Long temp, Long ah, String iata) {
        if(temp == null && ah == null) {
            return null;
        }
        return String.format(TEMP_AND_AH_PATTERN, iata, measureToString(temp), measureToString(ah));
    }
}
