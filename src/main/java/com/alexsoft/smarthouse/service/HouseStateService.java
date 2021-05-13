package com.alexsoft.smarthouse.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.ChartDto;
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

@Service
@RequiredArgsConstructor
public class HouseStateService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(HouseStateService.class);
    public static final String IN_PREFIX = "IN-";
    public static final String OUT_PREFIX = "OUT-";
    public static final String DATE = "Date";
    public static final Comparator<Object> OBJECT_TO_STRING_COMPARATOR = Comparator.comparing(o -> ((String) o));
    public static final String PM_10 = "PM10 ";
    public static final String PM_2_5 = "PM2.5 ";
    public static final String IAQ = "IAQ ";
    public static final String SIAQ = "SIAQ ";
    public static final String GR = "GR ";

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

    @Value("${sensor.bme680-temp-adjustment}")
    private final Double bme680TempAdjustment;

    private final HouseStateRepository houseStateRepository;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;

    public void save(String msg) {
        HouseState houseState = null;
        if (!msg.contains("{")) {
            return;
        }
        try {
            houseState = OBJECT_MAPPER.readValue(msg, HouseState.class);
            if (hasTempAndHumidMeasurements(houseState)) {
                Float aH = tempUtils.calculateAbsoluteHumidity(
                        houseState.getAir().getTemp().getCelsius().floatValue(),
                        houseState.getAir().getTemp().getRh()
                );
                houseState.getAir().getTemp().setAh(aH.doubleValue());
                if (houseState.getMeasurePlace().equals("OUT-NORTH")) {
                    houseState.getAir().getTemp().setCelsius(houseState.getAir().getTemp().getCelsius() + bme680TempAdjustment);
                }
            }
            if (houseState.getMeasurePlace().startsWith(IN_PREFIX)) { //  todo temporary, remove after changing the msg format on publishers
                houseState.setInOut(InOut.IN);
                houseState.setMeasurePlace(houseState.getMeasurePlace().replace(IN_PREFIX, ""));
            } else if (houseState.getMeasurePlace().startsWith(OUT_PREFIX)) {
                houseState.setInOut(InOut.OUT);
                houseState.setMeasurePlace(houseState.getMeasurePlace().replace(OUT_PREFIX, ""));
            }
            houseState.setMessageReceived(ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime());
            if (houseState.getMeasurePlace().equals("TERRACE") && houseState.getInOut() == InOut.IN) {
                return; // temporary disabled due to sensor malfunction
            }
            if (houseState.getMeasurePlace().equals("TERRACE") && houseState.getInOut() == InOut.OUT) {
                // temporary disabled due to sensor malfunction
                houseState.getAir().getTemp().setAh(null);
                houseState.getAir().getTemp().setRh(null);
            }
            save(houseState);
        } catch (JsonProcessingException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public void save(HouseState houseState) {
        if (msgSavingEnabled) {
            houseStateRepository.saveAndFlush(houseState);
        }
    }

    private boolean hasTempAndHumidMeasurements(HouseState houseState) {
        return houseState.getAir() != null && houseState.getAir().getTemp() != null &&
                houseState.getAir().getTemp().getCelsius() != null && houseState.getAir().getTemp().getRh() != null &&
                houseState.getAir().getTemp().getAh() == null;
    }

    public List<HouseState> findWithinInterval(final Integer minutes, final Integer hours, final Integer days) {
        return houseStateRepository.findAfter(dateUtils.getInterval(minutes, hours, days, true), dateUtils.getInterval(0, 0, 0, true));
    }

    public List<HouseState> findAll() {
        return houseStateRepository.findAll();
    }

    public ChartDto aggregate() {
        List<Map<String, Object>> aggregates = houseStateRepository.aggregate();
        ChartDto chartDto = new ChartDto();

        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);

        return chartDto;
    }

    private void setAqis(final List<Map<String, Object>> aggregates, final ChartDto chartDto) {

        Map<Timestamp, Object[]> aqiValuesMap = new TreeMap<>();
        List<Map<String, Object>> aqis = aggregates.stream().filter(map -> map.get("pm10") != null ||
            map.get("pm25") != null || map.get("iaq") != null).collect(Collectors.toList());
        List<Object> aqiHeader = new ArrayList<>();
        aqiHeader.add(DATE);
        aqiHeader.addAll(aqis.stream().map(m -> m.get("measure_place")).distinct().map(place ->
            List.of(PM_10 + place, PM_2_5 + place, IAQ + place, SIAQ + place, GR + place)).flatMap(Collection::stream).collect(Collectors.toList()));
        aqiHeader.sort(OBJECT_TO_STRING_COMPARATOR);

        Set<String> placesAdded = new LinkedHashSet<>();
        for (Map<String, Object> map : aqis) {
            Object pm10 = map.get("pm10");
            Object pm25 = map.get("pm25");
            Object iaq = map.get("iaq");
            Object siaq = map.get("siaq");
            Object gr = map.get("gas_resistance");
            String place = (String) map.get("measure_place");
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
        ).collect(Collectors.toList());
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
                .collect(Collectors.toList());
        List<Object> ahsHeader = new ArrayList<>();
        ahsHeader.add(DATE);
        for (Map<String, Object> map : ahs) {
            String place = (String) map.get("measure_place");
            if (!ahsHeader.contains(place)) {
                ahsHeader.add(place);
            }
        }
        ahsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : ahs) {
            Object dbAh = map.get("ah");
            String place = (String) map.get("measure_place");
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
                .collect(Collectors.toList());
        List<Object> rhsHeader = new ArrayList<>();
        rhsHeader.add(DATE);
        for (Map<String, Object> map : rhs) {
            String place = (String) map.get("measure_place");
            if (!rhsHeader.contains(place)) {
                rhsHeader.add(place);
            }
        }
        rhsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : rhs) {
            Object dbRh = map.get("rh");
            String place = (String) map.get("measure_place");
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
        List<Map<String, Object>> inTemp = aggregates.stream().filter(map -> map.get("in_out").equals("IN") && map.get("temp") != null)
            .collect(Collectors.toList());
        List<Object> inTempHeader = new ArrayList<>();
        inTempHeader.add(DATE);
        for (Map<String, Object> map : inTemp) {
            String place = (String) map.get("measure_place");
            if (!inTempHeader.contains(place)) {
                inTempHeader.add(place);
            }
        }
        inTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : inTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("measure_place");
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
        List<Map<String, Object>> outTemp = aggregates.stream().filter(map -> map.get("in_out").equals("OUT") && map.get("temp") != null)
                .collect(Collectors.toList());
        List<Object> outTempHeader = new ArrayList<>();
        outTempHeader.add(DATE);
        for (Map<String, Object> map : outTemp) {
            String place = (String) map.get("measure_place");
            if (!outTempHeader.contains(place)) {
                outTempHeader.add(place);
            }
        }
        outTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        for (Map<String, Object> map : outTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("measure_place");
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
}
