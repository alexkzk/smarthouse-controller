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

import com.alexsoft.smarthouse.db.entity.v1.AirQualityIndication;
import com.alexsoft.smarthouse.db.entity.v1.HeatIndication;
import com.alexsoft.smarthouse.db.entity.v1.HouseState;
import com.alexsoft.smarthouse.db.entity.v1.Measure;
import com.alexsoft.smarthouse.db.entity.v1.MeasurePlace;
import com.alexsoft.smarthouse.db.entity.v1.WindIndication;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.dto.mapper.HouseStateToDtoMapper;
import com.alexsoft.smarthouse.exception.BadRequestException;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.HouseStateMsgConverter;
import com.alexsoft.smarthouse.utils.TempUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.alexsoft.smarthouse.utils.HouseStateMsgConverter.MQTT_PRODUCER_TIMEZONE_ID;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class HouseStateService {

    private final static Logger LOGGER = LoggerFactory.getLogger(HouseStateService.class);

    @Value("${mqtt.msgSavingEnabled}")
    private final Boolean msgSavingEnabled;

    private final HouseStateRepository houseStateRepository;

    private final HouseStateToDtoMapper houseStateToDtoMapper;

    private final HouseStateMsgConverter houseStateMsgConverter;

    private final TempUtils tempUtils = new TempUtils();

    public HouseState save(String msg) {
        HouseState houseState = houseStateMsgConverter.toEntity(msg);
        if (!houseState.isNull() && msgSavingEnabled) {
            return houseStateRepository.saveAndFlush(houseState);
        }
        return null;
    }

    public Integer getFiveMinuteAvgIaq() {
        Float iaq = houseStateToDtoMapper.toDto(avgWithinInterval(5, 0, 0)).getOutdoorAqi().getStaticIaq();
        return iaq == null || Float.isNaN(iaq) ? 0 : iaq.intValue();
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

    public List<HouseStateDto> minutelyAggregatedMeasures() {
        return aggregateOnInterval(5, null, 1, null);
    }

    public List<HouseStateDto> hourlyAggregatedMeasures() {
        return aggregateOnInterval(60, null, null, 3);
    }

    public List<HouseStateDto> aggregateOnInterval(
            Integer aggregationIntervalMinutes, Integer minutes, Integer hours, Integer days
    ) {

        if (aggregationIntervalMinutes < 61 && aggregationIntervalMinutes % 5 != 0) {
            throw new BadRequestException("aggregationIntervalMinutes (less or equal 60) must be multiple of 5");
        } else if (aggregationIntervalMinutes % 60 != 0) {
            throw new BadRequestException("aggregationIntervalMinutes (greater than 60) must be multiple of 60");
        }

        LOGGER.debug("Aggregating houseStates on {} min interval, requested period: {} days, {} hours, {} minutes",
            aggregationIntervalMinutes, days, hours, minutes);
        long startMillis = System.currentTimeMillis();
        LocalDateTime interval = ZonedDateTime.now(MQTT_PRODUCER_TIMEZONE_ID).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days));
        List<HouseState> fetchedHouseStates = houseStateRepository.findAfter(interval);
        LOGGER.debug("Fetched {} houseStates, measures: {}, time: {} ms",
            fetchedHouseStates.size(), fetchedHouseStates.stream().flatMap(HouseState::getAllMeasures).count(),
            System.currentTimeMillis() - startMillis
        );
        if (aggregationIntervalMinutes != 0) {
            long aggregationStart = System.currentTimeMillis();
            List<HouseStateDto> houseStateDtos = fetchedHouseStates.stream().collect(
                Collectors.groupingBy(
                    houseState -> DateUtils.roundDateTime(houseState.getMessageReceived(), aggregationIntervalMinutes),
                    TreeMap::new,
                    Collectors.collectingAndThen(toList(), this::averageList)
                )
            ).entrySet().stream().peek(el -> el.getValue().setMessageReceived(el.getKey()))
                .map(Entry::getValue).sorted().map(houseStateToDtoMapper::toDto).collect(toList());
            LOGGER.debug("Aggregating completed, aggregation time: {} ms, total: {} ms", System.currentTimeMillis() - aggregationStart,
                System.currentTimeMillis() - startMillis);
            return houseStateDtos;
        }
        return fetchedHouseStates.stream().sorted().map(houseStateToDtoMapper::toDto).collect(toList());
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
                    .gasResistance((float) aqis.stream().map(AirQualityIndication::getGasResistance).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                    .iaqAccuracy( (int) Math.round(aqis.stream().map(AirQualityIndication::getIaqAccuracy).filter(Objects::nonNull).mapToInt(Integer::valueOf).average().orElse(Float.NaN)))
                    .staticIaq((float) aqis.stream().map(AirQualityIndication::getStaticIaq).filter(Objects::nonNull).mapToDouble(Double::valueOf).average().orElse(Double.NaN))
                    .maxIaq((float) aqis.stream().map(AirQualityIndication::getStaticIaq).filter(Objects::nonNull).mapToDouble(Double::valueOf).max().orElse(Double.NaN))
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
            if (heatIndication.getAbsoluteHumidity() == null && heatIndication.getRelativeHumidity() != null && heatIndication.getTempCelsius() != null) {
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
