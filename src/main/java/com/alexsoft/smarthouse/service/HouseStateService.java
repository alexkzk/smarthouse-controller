package com.alexsoft.smarthouse.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Measure;
import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.WindIndication;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.TempUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_PRODUCER_TIMEZONE_ID;
import static java.util.stream.Collectors.toList;

@Service
public class HouseStateService {

    private final static Logger LOGGER = LoggerFactory.getLogger(HouseStateService.class);

    private final HouseStateRepository houseStateRepository;
    private final HouseStateToDtoMapper houseStateToDtoMapper;
    private final TempUtils tempUtils = new TempUtils();

    public HouseStateService(HouseStateRepository houseStateRepository, HouseStateToDtoMapper houseStateToDtoMapper) {
        this.houseStateRepository = houseStateRepository;
        this.houseStateToDtoMapper = houseStateToDtoMapper;
    }

    public List<HouseStateDto> findDefaultMeasureSet() {
        List<HouseStateDto> hourlyList = aggregateOnInterval(60, null, null, 3);
        List<HouseStateDto> minutelyList = aggregateOnInterval(5, null, 1, null);
        return Stream.concat(minutelyList.stream(), hourlyList.stream()).collect(toList());
    }

    public List<HouseStateDto> findWithinInterval(Integer minutes, Integer hours, Integer days) {
        List<HouseState> measures = findHouseStates(minutes, hours, days);
        return houseStateToDtoMapper.toDtos(measures);
    }

    private List<HouseState> findHouseStates(Integer minutes, Integer hours, Integer days) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours));
        return houseStateRepository.findAfter(interval);
    }

    public HouseState avgWithinInterval(Integer minutes, Integer hours, Integer days) {
        return averageList(findHouseStates(minutes, hours, days));
    }

    public List<HouseStateDto> aggregateOnInterval(
            Integer aggregateIntervalMinutes, Integer minutes, Integer hours, Integer days
    ) {
        long startMillis = System.currentTimeMillis();
        LocalDateTime interval = ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days));
        List<HouseState> measures = houseStateRepository.findAfter(interval);
        LOGGER.debug("Started averaging of {} measures, fetching time was {}ms, nested measures: {}",
            measures.size(), System.currentTimeMillis() - startMillis,
            measures.stream().flatMap(HouseState::getAllMeasures).count()
        );
        startMillis = System.currentTimeMillis();
        List<HouseStateDto> houseStateDtos = measures.stream().collect(
                Collectors.groupingBy(
                        houseState -> DateUtils.roundDateTime(houseState.getMessageReceived(), aggregateIntervalMinutes),
                        TreeMap::new,
                        Collectors.collectingAndThen(toList(), this::averageList)
                )
        ).entrySet().stream().peek(el -> el.getValue().setMessageReceived(el.getKey()))
                .map(Entry::getValue).sorted().map(houseStateToDtoMapper::toDto).collect(toList());
        LOGGER.debug("Averaging completed, time {}", System.currentTimeMillis() - startMillis);
        return houseStateDtos;
    }

    private HouseState averageList(List<HouseState> houseStates) {

        long startMillis = System.currentTimeMillis();

        HouseState averagedHouseState = new HouseState();

        List<Measure> measures = houseStates.stream().flatMap(HouseState::getAllMeasures).peek(measure -> {
            if (measure instanceof AirQualityIndication) {
                measure.setMeasurePlace(MeasurePlace.OUTDOOR);
            } else if (measure.getMeasurePlace() == MeasurePlace.CHILDRENS && measure instanceof HeatIndication) {
                measure.setMeasurePlace(MeasurePlace.BALCONY);
            }
        }).collect(Collectors.toList());
        Set<MeasurePlace> measurePlaces = measures.stream().map(Measure::getMeasurePlace).collect(Collectors.toSet());

        for (MeasurePlace measurePlace : measurePlaces) {

            List<AirQualityIndication> aqis = houseStates.stream().flatMap(houseState -> houseState.getAirQualities().stream())
                .filter(aqi -> aqi.getMeasurePlace() == measurePlace)
                .collect(Collectors.toList());
            AirQualityIndication averagedAqi = AirQualityIndication.builder()
                .measurePlace(measurePlace)
                .pm25((float) aqis.stream().map(AirQualityIndication::getPm25).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .pm10((float) aqis.stream().map(AirQualityIndication::getPm10).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .co2((float) aqis.stream().map(AirQualityIndication::getCo2).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .iaq((float) aqis.stream().map(AirQualityIndication::getIaq).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .voc((float) aqis.stream().map(AirQualityIndication::getVoc).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                .build();

            List<HeatIndication> temps = houseStates.stream().flatMap(houseState -> houseState.getHeatIndications().stream())
                .filter(heatIndication -> heatIndication.getMeasurePlace() == measurePlace)
                .collect(Collectors.toList());
            double averageRh = temps.stream().filter(temp -> temp.getRelativeHumidity() != null).mapToInt(HeatIndication::getRelativeHumidity).average().orElse(Double.NaN);
            HeatIndication heatIndication = HeatIndication.builder()
                .measurePlace(measurePlace)
                .tempCelsius((float) temps.stream().filter(temp -> temp.getTempCelsius() != null).mapToDouble(HeatIndication::getTempCelsius).average().orElse(Double.NaN))
                .absoluteHumidity((float) temps.stream().filter(temp -> temp.getAbsoluteHumidity() != null).mapToDouble(HeatIndication::getAbsoluteHumidity).average().orElse(Double.NaN))
                .relativeHumidity(Double.isNaN(averageRh) ? null : (int) Math.round(averageRh))
                .build();
            if(heatIndication.getAbsoluteHumidity() == null && heatIndication.getRelativeHumidity() != null && heatIndication.getTempCelsius() != null) {
                heatIndication.setAbsoluteHumidity(tempUtils.calculateAbsoluteHumidity(heatIndication.getTempCelsius(), heatIndication.getRelativeHumidity()));
            }

            List<WindIndication> winds = houseStates.stream().flatMap(houseState -> houseState.getWindIndications().stream())
                .filter(windIndication -> windIndication.getMeasurePlace() == measurePlace)
                .collect(Collectors.toList());
            double windDir = winds.stream().filter(temp -> temp.getDirection() != null).mapToInt(WindIndication::getDirection).average().orElse(Double.NaN);
            double windSpeed = winds.stream().filter(temp -> temp.getDirection() != null).mapToInt(WindIndication::getSpeed).average().orElse(Double.NaN);
            WindIndication averagedWinds = WindIndication.builder()
                .measurePlace(measurePlace)
                .direction(Double.isNaN(windDir) ? null : (int) Math.round(windDir))
                .speed(Double.isNaN(windSpeed) ? null : (int) Math.round(windSpeed))
                .build();

            averagedHouseState.addIndication(averagedWinds);
            averagedHouseState.addIndication(heatIndication);
            averagedHouseState.addIndication(averagedAqi);

        }

        LOGGER.trace("Averaged a list of HouseState's of size {}, time {}ms, averaged value: {}",
            houseStates.size(), System.currentTimeMillis() - startMillis, averagedHouseState);

        return averagedHouseState;

    }

}
