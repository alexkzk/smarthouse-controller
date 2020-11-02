package com.alexsoft.smarthouse.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.db.entity.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.HeatIndication;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Measure;
import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.WindIndication;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import com.alexsoft.smarthouse.utils.SerializationUtils;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_PRODUCER_TIMEZONE_ID;
import static java.util.stream.Collectors.toList;

@Service
public class HouseStateService {
    private final HouseStateRepository houseStateRepository;
    private final HouseStateToDtoMapper houseStateToDtoMapper;

    public HouseStateService(HouseStateRepository houseStateRepository, HouseStateToDtoMapper houseStateToDtoMapper) {
        this.houseStateRepository = houseStateRepository;
        this.houseStateToDtoMapper = houseStateToDtoMapper;
    }

    public List<HouseStateDto> findWithinMinutes(Integer minutes) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes));
        List<HouseState> measures = houseStateRepository.findAfter(interval);
        SerializationUtils.serializeToFile("temp.json", measures);
        return houseStateToDtoMapper.toDtos(measures);

    }

    public List<HouseState> aggregateOnInterval(Integer aggregateIntervalMinutes, Integer withinMinutes) {
        LocalDateTime interval = ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
                .minus(Duration.ofMinutes(withinMinutes));
        List<HouseState> measures = houseStateRepository.findAfter(interval);
        return measures.stream().collect(
                Collectors.groupingBy(
                        houseState -> houseState.getMessageReceived().withSecond(0).withNano(0)
                            .withMinute(houseState.getMessageReceived().getMinute() / aggregateIntervalMinutes * aggregateIntervalMinutes),
                        TreeMap::new,
                        Collectors.collectingAndThen(toList(), this::averageList)
                )
        ).entrySet().stream().peek(el -> el.getValue().setMessageReceived(el.getKey())).map(Entry::getValue).collect(toList());
    }

    private HouseState averageList(List<HouseState> houseStates) {

        HouseState averagedHouseState = new HouseState();

        List<Measure> measures = houseStates.stream().flatMap(HouseState::getAllMeasures).collect(Collectors.toList());
        Set<MeasurePlace> measurePlaces = measures.stream().map(Measure::getMeasurePlace).collect(Collectors.toSet());

        for (MeasurePlace measurePlace : measurePlaces) {

            List<AirQualityIndication> aqis = houseStates.stream().flatMap(houseState -> houseState.getAirQualities().stream())
                .filter(aqi -> aqi.getMeasurePlace() == measurePlace)
                .collect(Collectors.toUnmodifiableList());
            AirQualityIndication averagedAqi = AirQualityIndication.builder()
                .measurePlace(measurePlace)
                .pm25((float) aqis.stream().filter(aqi -> aqi.getPm25() != null).mapToDouble(AirQualityIndication::getPm25).average().orElse(Double.NaN))
                .pm10((float) aqis.stream().filter(aqi -> aqi.getPm10() != null).mapToDouble(AirQualityIndication::getPm10).average().orElse(Double.NaN))
                .co2((float) aqis.stream().filter(aqi -> aqi.getCo2() != null).mapToDouble(AirQualityIndication::getCo2).average().orElse(Double.NaN))
                .iaq((float) aqis.stream().filter(aqi -> aqi.getIaq() != null).mapToDouble(AirQualityIndication::getIaq).average().orElse(Double.NaN))
                .voc((float) aqis.stream().filter(aqi -> aqi.getVoc() != null).mapToDouble(AirQualityIndication::getVoc).average().orElse(Double.NaN))
                .build();

            List<HeatIndication> temps = houseStates.stream().flatMap(houseState -> houseState.getHeatIndications().stream())
                .filter(heatIndication -> heatIndication.getMeasurePlace() == measurePlace)
                .collect(Collectors.toUnmodifiableList());
            double averageRh = temps.stream().filter(temp -> temp.getRelativeHumidity() != null).mapToInt(HeatIndication::getRelativeHumidity).average().orElse(Double.NaN);
            HeatIndication heatIndication = HeatIndication.builder()
                .measurePlace(measurePlace)
                .tempCelsius((float) temps.stream().filter(temp -> temp.getTempCelsius() != null).mapToDouble(HeatIndication::getTempCelsius).average().orElse(Double.NaN))
                .absoluteHumidity((float) temps.stream().filter(temp -> temp.getAbsoluteHumidity() != null).mapToDouble(HeatIndication::getAbsoluteHumidity).average().orElse(Double.NaN))
                .relativeHumidity(Double.isNaN(averageRh) ? null : (int) Math.round(averageRh))
                .build();

            List<WindIndication> winds = houseStates.stream().flatMap(houseState -> houseState.getWindIndications().stream())
                .filter(windIndication -> windIndication.getMeasurePlace() == measurePlace)
                .collect(Collectors.toUnmodifiableList());
            double windDir = winds.stream().filter(temp -> temp.getDirection() != null).mapToInt(WindIndication::getDirection).average().orElse(Double.NaN);
            double windSpeed = winds.stream().filter(temp -> temp.getDirection() != null).mapToInt(WindIndication::getSpeed).average().orElse(Double.NaN);
            WindIndication averagedWinds = WindIndication.builder()
                .measurePlace(measurePlace)
                .direction(Double.isNaN(windDir) ? null : (int) Math.round(windDir))
                .direction(Double.isNaN(windSpeed) ? null : (int) Math.round(windSpeed))
                .build();

            averagedHouseState.addIndication(averagedWinds);
            averagedHouseState.addIndication(heatIndication);
            averagedHouseState.addIndication(averagedAqi);

        }

        return averagedHouseState;

    }

}
