package com.alexsoft.smarthouse.service;

import static com.alexsoft.smarthouse.utils.Constants.DATE;
import static com.alexsoft.smarthouse.utils.Constants.GR;
import static com.alexsoft.smarthouse.utils.Constants.IAQ;
import static com.alexsoft.smarthouse.utils.Constants.IN_PREFIX;
import static com.alexsoft.smarthouse.utils.Constants.MIAMI_MEASURE_PLACE;
import static com.alexsoft.smarthouse.utils.Constants.NORTH_MEASURE_PLACE;
import static com.alexsoft.smarthouse.utils.Constants.OUTSIDE_STATUS_PATTERN;
import static com.alexsoft.smarthouse.utils.Constants.OUT_PREFIX;
import static com.alexsoft.smarthouse.utils.Constants.PM_10;
import static com.alexsoft.smarthouse.utils.Constants.PM_2_5;
import static com.alexsoft.smarthouse.utils.Constants.SEATTLE_MEASURE_PLACE;
import static com.alexsoft.smarthouse.utils.Constants.SIAQ;
import static com.alexsoft.smarthouse.utils.Constants.SOUTH_MEASURE_PLACE;
import static com.alexsoft.smarthouse.utils.Constants.S_OCEAN_DR_HOLLYWOOD;
import static com.alexsoft.smarthouse.utils.MathUtils.doubleToInt;
import static com.alexsoft.smarthouse.utils.MathUtils.getNumberOrString;
import static com.alexsoft.smarthouse.utils.MathUtils.round;
import static java.util.stream.Collectors.toList;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.db.entity.*;
import com.alexsoft.smarthouse.db.repository.IndicationRepository;
import com.alexsoft.smarthouse.db.repository.VisitRepository;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.TempUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndicationService.class);

    public static final Comparator<Object> OBJECT_TO_STRING_COMPARATOR = Comparator.comparing(o -> ((String) o));

    private final IndicationRepository indicationRepository;
    private final VisitRepository visitRepository;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;
    private final MetarLocationsConfig metarLocationsConfig;

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

    @Value("${smarthouse.short-status-null-string}")
    private String shortStatusNullString;

    @Value("${smarthouse.db.port}")
    private String dbPort;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${smarthouse.db.hostname}")
    private String dbHostname;

    @Value("#{T(java.lang.System).getProperty('user.home') + '/smarthouse'}/")
    private String dbDumpFilePath;

    @Value("${smarthouse.db.filename}")
    private String dbDumpFilename;

    @Value("${smarthouse.db.dump-on-destroy}")
    private boolean dumpOnDestroy;

    @Value("${smarthouse.db.restore-on-startup}")
    private boolean restoreOnStartup;

    @Value("${smarthouse.ftp.hostname}")
    private String ftpHostname;

    @Value("${smarthouse.ftp.username}")
    private String ftpUsername;

    @Value("${smarthouse.ftp.password}")
    private String ftpPassword;

    @Value("${smarthouse.ftp.remote-path}")
    private String ftpRemotePath;

    @Value("${smarthouse.ftp.upload-on-destroy}")
    private boolean uploadDumpOnDestroy;

    @PostConstruct
    public void init() {
        if (restoreOnStartup) {
            String downloadedDump = downloadFromFtp();
            if (downloadedDump != null) {
                LOGGER.info("psql restore started");
                long start = System.currentTimeMillis();
                Process p;
                try {
                    p = new ProcessBuilder("/usr/lib/pgsql/bin/psql", "--file=" + downloadedDump, "--username=" + dbUsername,
                            "--host=" + dbHostname, "--port=" + dbPort).start();
                    p.waitFor();
                    LOGGER.info("Database restored. Time: {} ms", System.currentTimeMillis() - start);
                } catch (Exception e) {
                    LOGGER.error("Restoring database failed", e);
                }
            } else {
                LOGGER.warn("No DB dump to restore");
            }
        }
    }

    @PreDestroy
    public void preDestroy() {
        if (!dumpOnDestroy) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            LOGGER.info("pg_dump started: {} {} {} {} {} {} {} {}", "/usr/bin/pg_dump", "--file=" + dbDumpFilePath + dbDumpFilename, "--username=" + dbUsername,
                    "--host=" + dbHostname, "--port=" + dbPort, "-w", "--clean", "--if-exists");
            Process p;
            p = new ProcessBuilder("/usr/bin/pg_dump", "--file=" + dbDumpFilePath + dbDumpFilename, "--username=" + dbUsername,
                    "--host=" + dbHostname, "--port=" + dbPort, "-w", "--clean", "--if-exists").start();
            p.waitFor();
            Path path = Paths.get(dbDumpFilePath + dbDumpFilename);
            Long mb = null;
            try {
                mb = Files.size(path) / 1024 / 1024;
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
            }
            LOGGER.info("pg_dump process completed successfully. Time: %d ms. Dump size: %d MB".formatted(
                    System.currentTimeMillis() - start, mb));
            if (uploadDumpOnDestroy) {
                LOGGER.info("Uploading to FTP %s started".formatted(ftpHostname));
                start = System.currentTimeMillis();
                if (uploadToFtp()) {
                    LOGGER.info(
                            "Uploaded to FTP %s. Time: %d ms".formatted(ftpHostname, System.currentTimeMillis() - start));
                } else {
                    LOGGER.warn("Uploading to FTP %s failed. Time: %d ms".formatted(ftpHostname,
                            System.currentTimeMillis() - start));
                }
            }

        } catch (Exception e) {
            LOGGER.warn("pg_dump process failed, %s, time taken: %d ms".formatted(e.getMessage(),
                    System.currentTimeMillis() - start));
        }
    }

    public List<Indication> aggregateOnInterval(Integer amount, TemporalUnit temporalUnit, Integer minutes, Integer hours, Integer days, String remoteAddr) {
        LOGGER.debug("Aggregating houseStates on {} min startDate, requested period: {} days, {} hours, {} minutes",
                amount, temporalUnit, days, hours, minutes);
        logVisit(remoteAddr);

        LocalDateTime startDate = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime()
                .minus(Duration.ofMinutes(minutes == null || minutes < 0 ? 0 : minutes))
                .minus(Duration.ofHours(hours == null || hours < 0 ? 0 : hours))
                .minus(Duration.ofDays(days == null || days < 0 ? 0 : days));

        return aggregateOnInterval(amount, temporalUnit, startDate, LocalDateTime.now());
    }

    private void logVisit(String remoteAddr) {
        Visit visit = new Visit();
        visit.setTime(LocalDateTime.now());
        visit.setIpAddress(remoteAddr);
        visitRepository.save(visit);
    }

    public void createAverageMeasurement(Integer amount, TemporalUnit temporalUnit) {
        LocalDateTime endDate = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime().withSecond(0).withNano(0);
        LocalDateTime startDate = endDate.minus(amount, temporalUnit);
        createAverageMeasurement(amount, temporalUnit, startDate, endDate);
    }

    public void createAverageMeasurement(Integer amount, TemporalUnit temporalUnit, LocalDateTime startDate, LocalDateTime endDate) {
        LOGGER.info("Start date: {}, end date: {}", startDate, endDate);
        List<Indication> indications = aggregateOnInterval(amount, temporalUnit, startDate, endDate);
        indications.forEach(indication -> {
            indication.setAggregationPeriod(AggregationPeriod.of(temporalUnit));
            String timeZone = metarLocationsConfig.getLocationMapping().get(indication.getIndicationPlace()).values().stream().findFirst().get();
            indication.setReceivedLocal(dateUtils.ttoLocalDateTimeAtZone(indication.getReceivedUtc(), timeZone));
        });
        List<Indication> savedIndications = saveAll(indications);
        LOGGER.info("Saved {} aggregated measurements for the following interval: {} - {}. Aggregation period: {} {}.",
                savedIndications.size(), startDate, endDate, amount, temporalUnit);
        LOGGER.debug(savedIndications.toString());
    }

    public List<Indication> aggregateOnInterval(Integer amount, TemporalUnit temporalUnit, LocalDateTime startDate, LocalDateTime endDate) {

        long startMillis = System.currentTimeMillis();
        List<Indication> fetchedHouseStates;

        if (temporalUnit == ChronoUnit.MONTHS) {
            fetchedHouseStates = indicationRepository.findBetween(startDate, endDate, AggregationPeriod.DAILY);
        } else {
            fetchedHouseStates = indicationRepository.findBetween(startDate, endDate);
        }

        if (amount != null && 0 != amount) {
            long aggregationStart = System.currentTimeMillis();
            List<Indication> indications = fetchedHouseStates.stream().collect(
                    Collectors.groupingBy(ind -> ind.getIndicationPlace() + "&" + ind.getInOut())
                    ).entrySet().stream().flatMap(
                            entry -> aggregateByInterval(
                                    amount, temporalUnit, entry.getValue(), entry.getKey().split("&")[0],
                                    InOut.valueOf(entry.getKey().split("&")[1])
                            ).stream()).sorted().collect(Collectors.toList());

            LOGGER.debug("Aggregating completed, aggregation time: {} ms, total: {} ms", System.currentTimeMillis() - aggregationStart,
                    System.currentTimeMillis() - startMillis);

            return indications;
        } else {
            return fetchedHouseStates.stream().sorted().collect(toList());
        }
    }

    private List<Indication> aggregateByInterval(Integer amount, TemporalUnit temporalUnit, List<Indication> fetchedHouseStates, String indicationPlace, InOut inOut) {
        LOGGER.info("Aggregating {} measurements for {} {} indication place", fetchedHouseStates.size(), indicationPlace, inOut);
        if (amount == 1 && temporalUnit == ChronoUnit.DAYS) {
            // That's needed because of picking up different day when fetched within last 24 hours to avoid aggregating by two days e.g. 17 Jun 01:00 and 16 Jun 23:00
            int minDay = fetchedHouseStates.stream().mapToInt(st -> st.getReceivedUtc().getDayOfMonth()).min().orElseThrow();
            fetchedHouseStates.forEach(st -> st.setReceivedUtc(st.getReceivedUtc().withDayOfMonth(minDay)));
        }
        return fetchedHouseStates.stream().collect(
                        Collectors.groupingBy(
                                houseState -> dateUtils.roundDateTime(houseState.getReceivedUtc(), amount, temporalUnit),
                                TreeMap::new,
                                Collectors.collectingAndThen(toList(), indications -> averageList(indications, indicationPlace, inOut))
                        )
                ).entrySet().stream().peek(el -> el.getValue().setReceivedUtc(el.getKey()))
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

    public List<Indication> saveAll(List<Indication> indicationsToSave) {
        if (msgSavingEnabled) {
            return indicationRepository.saveAll(indicationsToSave);
        } else {
            LOGGER.debug("Skipping of saving indications");
            return Collections.emptyList();
        }
    }

    public Indication save(Indication indicationToSave, boolean normalize, AggregationPeriod aggregationPeriod) {

        if (msgSavingEnabled) {
            if (normalize) {
                indicationToSave.setAggregationPeriod(aggregationPeriod);
                normalizeTempAndHumidValues(indicationToSave);
                calculateAbsoluteHumidity(indicationToSave);
                setInOut(indicationToSave);
                resetTempAndHumidForPlace("TERRACE", indicationToSave);
                setEmptyMeasurementsToNull(indicationToSave);
                convertPressureToMmHg(indicationToSave);
            }
            Indication savedIndication = indicationRepository.saveAndFlush(indicationToSave);
            LOGGER.debug("Saved indication {}", savedIndication);
            return savedIndication;
        } else {
            LOGGER.debug("Skipping of saving indication {}", indicationToSave);
            return null;
        }
    }

    private boolean hasNoAhCalculated(Indication indication) {
        return indication.getAir() != null && indication.getAir().getTemp() != null &&
                indication.getAir().getTemp().getCelsius() != null && indication.getAir().getTemp().getRh() != null &&
                indication.getAir().getTemp().getAh() == null;
    }

    public List<Indication> findHourly() {
        return indicationRepository.findBetween(dateUtils.getInterval(15, 0, 0, true), dateUtils.getInterval(0, 0, 0, true));
    }

    public String getHourlyAveragedShortStatus(String remoteAddr) {
        logVisit(remoteAddr);

        List<Indication> hourlyAverage = findHourly().stream().filter(hst -> hst.getInOut() == InOut.OUT).collect(toList());

        List<Indication> northMeasurements = filterByPlace(hourlyAverage, NORTH_MEASURE_PLACE);
        List<Indication> southMeasurements = filterByPlace(hourlyAverage, SOUTH_MEASURE_PLACE);
        List<Indication> seattleMeasurements = filterByPlace(hourlyAverage, SEATTLE_MEASURE_PLACE);
        List<Indication> miamiMeasurements = filterByPlace(hourlyAverage, MIAMI_MEASURE_PLACE);

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

        boolean actualMeasuresNorth;

        if (southTemp == null) {
            actualMeasuresNorth = true;
        } else if (northTemp == null) {
            actualMeasuresNorth = false;
        } else {
            actualMeasuresNorth = northTemp < southTemp;
        }

        return String.format(OUTSIDE_STATUS_PATTERN,
                actualMeasuresNorth ? "N" : "S",
                actualMeasuresNorth ? getNumberOrString(northTemp, shortStatusNullString) : getNumberOrString(southTemp, shortStatusNullString),
                getNumberOrString(seattleTemp, shortStatusNullString),    //todo refactor getNumberOrString so MathUtils be a Spring component and inject shortStatusNullString by itself
                getNumberOrString(miamiTemp, shortStatusNullString),
                actualMeasuresNorth ? getNumberOrString(northAh, shortStatusNullString) : getNumberOrString(southAh, shortStatusNullString),
                getNumberOrString(seattleAh, shortStatusNullString),
                getNumberOrString(miamiAh, shortStatusNullString)
        );
    }

    public Integer getAverageChornomorskTemp(String remoteAddr) {
        logVisit(remoteAddr);

        List<Indication> hourlyAverage = findHourly().stream().filter(hst -> hst.getInOut() == InOut.OUT).collect(toList());

        List<Indication> northMeasurements = filterByPlace(hourlyAverage, NORTH_MEASURE_PLACE);
        List<Indication> southMeasurements = filterByPlace(hourlyAverage, SOUTH_MEASURE_PLACE);

        Long southTemp = calculateAverageTemperature(southMeasurements);
        Long northTemp = calculateAverageTemperature(northMeasurements);

        boolean actualMeasuresNorth;

        if (southTemp == null && northTemp == null) {
            return null;
        } else if (southTemp == null) {
            actualMeasuresNorth = true;
        } else if (northTemp == null) {
            actualMeasuresNorth = false;
        } else {
            actualMeasuresNorth = northTemp < southTemp;
        }

        return actualMeasuresNorth ? northTemp.intValue() : southTemp.intValue();

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
        return indicationRepository.findAll();
    }

    public List<Indication> findRecent(String remoteAddr) {
        logVisit(remoteAddr);
        return indicationRepository.findBetween(LocalDateTime.now().minusDays(7), LocalDateTime.now(), AggregationPeriod.HOURLY);
    }

    public ChartDto getAggregatedData(String remoteAddr) {
        logVisit(remoteAddr);
        List<Map<String, Object>> aggregates = indicationRepository.getAggregatedSqlAveraged();
        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }

    public ChartDto getAggregatedDataV2(String remoteAddr) {
        logVisit(remoteAddr);
        List<Map<String, Object>> aggregates = indicationRepository.getAggregatedHourlyAndMinutely();
        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }

    public ChartDto getAggregatedDataDaily(String place, String period, String remoteAddr) {
        logVisit(remoteAddr);
        if ("HOLLYWOOD".equalsIgnoreCase(place)) {
            place = S_OCEAN_DR_HOLLYWOOD;
        }
        List<Map<String, Object>> aggregates;

        if (!"MONTHLY".equals(period)) {
            aggregates = indicationRepository.getAggregatedDaily(StringUtils.isBlank(place) ? "%" : place, StringUtils.isBlank(period) ? "%" : period);
        } else {
            aggregates = indicationRepository.getAggregatedMonthly(StringUtils.isBlank(place) ? "%" : place, StringUtils.isBlank(period) ? "%" : period);
        }

        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }

    /*public ChartDto getAggregatedDataDailyV2(String place) {
        Query q = em.createNativeQuery("select received_utc as msg_received,\n"
                + "       in_out,\n"
                + "       indication_place,\n"
                + "       aggregation_period as period,\n"
                + "       round(CAST(float8(celsius) as numeric), 1)                  as temp,\n"
                + "       round(CAST(float8(rh) as numeric), 0)                       as rh,\n"
                + "       round(CAST(float8(ah) as numeric), 1)                       as ah,\n"
                + "       round(CAST(float8(mm_hg) as numeric), 0)                    as mm_hg,\n"
                + "       round(CAST(float8(direction) as numeric), 0) as direction,\n"
                + "       round(CAST(float8(speed_ms) as numeric), 0)  as speed_ms\n"
                + "from main.indication\n"
                + "         left join main.air a on a.id = indication.air_id\n"
                + "         left join main.air_temp_indication t on t.id = a.temp_id\n"
                + "         left join main.air_pressure_indication ap on ap.id = a.pressure_id\n"
                + "         left join main.air_wind_indication w on w.id = a.wind_id\n"
                + "where indication_place LIKE :place"
                + "  AND DATE_PART('day', AGE(now() at time zone 'utc', received_utc)) <= 5\n"
                + "  AND DATE_PART('month', AGE(now() at time zone 'utc', received_utc)) = 0\n"
                + "  AND DATE_PART('year', AGE(now() at time zone 'utc', received_utc)) = 0\n"
                + "  AND aggregation_period = 'DAILY' order by msg_received desc");
        q.setParameter("place", StringUtils.isBlank(place) ? "%" : place);

        List<Map<String, Object>> aggregates = q.getResultList();
        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }*/

    private void setColors(ChartDto chartDto) {
        Object[] aqiColors = new Object[]{"#ff0000", "#791d00", "#e27f67", "#969eff", "#4254f5"};
        chartDto.setAqiColors(aqiColors);

        Object[] outdoorTemp = (Object[]) chartDto.getOutdoorTemps()[0];
        List<Object> outdoorTempHeader = Arrays.stream(outdoorTemp).collect(toList());
        outdoorTempHeader = outdoorTempHeader.subList(1, outdoorTempHeader.size());
        Object[] outdoorColors = outdoorTempHeader.stream().map(s -> intToARGB(s.hashCode())).toArray();
        chartDto.setOutdoorColors(outdoorColors);

        Object[] rhs = (Object[]) chartDto.getRhs()[0];
        List<Object> rhHeader = Arrays.stream(rhs).collect(toList());
        rhHeader = rhHeader.subList(1, rhHeader.size());
        Object[] rhColors = rhHeader.stream().map(s -> intToARGB(s.hashCode())).toArray();
        chartDto.setRhsColors(rhColors);

        Object[] ahs = (Object[]) chartDto.getAhs()[0];
        List<Object> ahHeader = Arrays.stream(ahs).collect(toList());
        ahHeader = ahHeader.subList(1, ahHeader.size());
        Object[] ahColors = ahHeader.stream().map(s -> intToARGB(s.hashCode())).toArray();
        chartDto.setAhsColors(ahColors);

        Object[] indoor = (Object[]) chartDto.getIndoorTemps()[0];
        List<Object> indoorHeader = Arrays.stream(indoor).collect(toList());
        indoorHeader = indoorHeader.subList(1, indoorHeader.size());
        Object[] indoorColors = indoorHeader.stream().map(s -> intToARGB(s.hashCode())).toArray();
        chartDto.setIndoorColors(indoorColors);

    }

    public static String intToARGB(int i){
        if (i == "LOS-ANGELES".hashCode()) {
            i = "LOS-ANGELES".toLowerCase().hashCode();
        } else if (i == S_OCEAN_DR_HOLLYWOOD.hashCode()){
            i = "HOLLYWOOD-FL".hashCode();
        }
        String color = Integer.toHexString(((i >> 24) & 0xFF)) +
                Integer.toHexString(((i >> 16) & 0xFF)) +
                Integer.toHexString(((i >> 8) & 0xFF)) +
                Integer.toHexString((i & 0xFF));
        return "#" + color.substring(0, Math.min(color.length(), 6));
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
            valueArr[aqiHeader.indexOf(DATE)] = map.get("period").equals("MONTHLY") ? dateUtils.timestampToLocalDateTimeString(msgReceivedTs, DateTimeFormatter.ofPattern("MMM YY")) : dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
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
        for (Map<String, Object> map : ahs) {
            String place = (String) map.get("indication_place");
            if (!ahsHeader.contains(place)) {
                ahsHeader.add(place);
            }
        }
        ahsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        ahsHeader.add(0, DATE);
        for (Map<String, Object> map : ahs) {
            Object dbAh = map.get("ah");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = ahsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[ahsHeader.size()]);
            valueArr[ahsHeader.indexOf(place)] = ((BigDecimal) dbAh).doubleValue();
            valueArr[ahsHeader.indexOf(DATE)] = map.get("period").equals("MONTHLY") ? dateUtils.timestampToLocalDateTimeString(msgReceivedTs, DateTimeFormatter.ofPattern("MMM YY")) : dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
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
        for (Map<String, Object> map : rhs) {
            String place = (String) map.get("indication_place");
            if (!rhsHeader.contains(place)) {
                rhsHeader.add(place);
            }
        }
        rhsHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        rhsHeader.add(0, DATE);
        for (Map<String, Object> map : rhs) {
            Object dbRh = map.get("rh");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = rhsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[rhsHeader.size()]);
            valueArr[rhsHeader.indexOf(place)] = ((BigDecimal) dbRh).doubleValue();
            valueArr[rhsHeader.indexOf(DATE)] = map.get("period").equals("MONTHLY") ? dateUtils.timestampToLocalDateTimeString(msgReceivedTs, DateTimeFormatter.ofPattern("MMM YY")) : dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
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
        for (Map<String, Object> map : inTemp) {
            String place = (String) map.get("indication_place");
            if (!inTempHeader.contains(place)) {
                inTempHeader.add(place);
            }
        }
        inTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        inTempHeader.add(0, DATE);
        for (Map<String, Object> map : inTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = inTempsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[inTempHeader.size()]);
            valueArr[inTempHeader.indexOf(place)] = ((BigDecimal) dbTemp).doubleValue();
            valueArr[inTempHeader.indexOf(DATE)] = map.get("period").equals("MONTHLY") ? dateUtils.timestampToLocalDateTimeString(msgReceivedTs, DateTimeFormatter.ofPattern("MMM YY")) : dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
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
        for (Map<String, Object> map : outTemp) {
            String place = (String) map.get("indication_place");
            if (!outTempHeader.contains(place)) {
                outTempHeader.add(place);
            }
        }
        outTempHeader.sort(OBJECT_TO_STRING_COMPARATOR);
        outTempHeader.add(0, DATE);
        for (Map<String, Object> map : outTemp) {
            Object dbTemp = map.get("temp");
            String place = (String) map.get("indication_place");
            Timestamp msgReceivedTs = (Timestamp) map.get("msg_received");
            Object[] valueArr = outTempsMap.computeIfAbsent(msgReceivedTs, (date) -> new Object[outTempHeader.size()]);
            valueArr[outTempHeader.indexOf(place)] = ((BigDecimal) dbTemp).doubleValue();
            valueArr[outTempHeader.indexOf(DATE)] = map.get("period").equals("MONTHLY") ? dateUtils.timestampToLocalDateTimeString(msgReceivedTs, DateTimeFormatter.ofPattern("MMM YY")) : dateUtils.timestampToLocalDateTimeString(msgReceivedTs);
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

    public List<Indication> findAfter(String date, AggregationPeriod period, String place, String remoteAddr) {
        logVisit(remoteAddr);
        LocalDateTime localDateTime = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm"));
        LocalDateTime utcLocalDateTime = ZonedDateTime.of(localDateTime, ZoneId.of("Europe/Kiev"))
                .withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
        return indicationRepository.findAfterAndPeriodAndPlace(utcLocalDateTime, period, place);
    }

    public boolean uploadToFtp() {
        FTPClient ftpClient = new FTPClient();
        try {
            ftpClient.connect(ftpHostname, 21);
            ftpClient.login(ftpUsername, ftpPassword);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            File LocalFile = new File(dbDumpFilePath + dbDumpFilename);
            String[] split = dbDumpFilePath.split("/");
            String remoteFileName = split[split.length - 1];
            String remoteFile = ftpRemotePath + "/" + remoteFileName;
            InputStream inputStream = new FileInputStream(LocalFile);
            boolean done = ftpClient.storeFile(remoteFile, inputStream);
            inputStream.close();
            if (done) {
                return true;
            }
        } catch (IOException ex) {
            LOGGER.error("Uploading db dump to FTP failed", ex);
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                LOGGER.error("Closing connection to FTP failed", ex);
            }
        }
        return false;
    }

    public String downloadFromFtp() {

        FTPClient ftpClient = new FTPClient();
        try {

            ftpClient.connect(ftpHostname, 21);
            ftpClient.login(ftpUsername, ftpPassword);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            long start = System.currentTimeMillis();
            File downloadedDump = new File(dbDumpFilePath + dbDumpFilename);
            downloadedDump.createNewFile();
            OutputStream outputStream2 = new BufferedOutputStream(new FileOutputStream(downloadedDump));
            String ftpFilePath = "G/backup/smarthouse/" + dbDumpFilename;
            InputStream inputStream = ftpClient.retrieveFileStream(ftpFilePath);
            LOGGER.info("Downloading {} from ftp {}", ftpFilePath, ftpHostname);
            byte[] bytesArray = new byte[4096];
            int bytesRead = -1;
            if (inputStream == null) {
                LOGGER.warn("DB dump was not found on the FTP server");
                return null;
            }
            while ((bytesRead = inputStream.read(bytesArray)) != -1) {
                outputStream2.write(bytesArray, 0, bytesRead);
            }

            if (ftpClient.completePendingCommand()) {
                Path path = Paths.get(dbDumpFilePath + dbDumpFilename);
                Long mb = null;
                try {
                    mb = Files.size(path) / 1024 / 1024;
                } catch (Exception e) {
                    LOGGER.error("Error during calculating the downloaded file size {}", downloadedDump);
                    return null;
                }

                LOGGER.info("{} downloaded, time: {} ms, size: {} MB", dbDumpFilePath + dbDumpFilename, System.currentTimeMillis() - start, mb);
            }
            outputStream2.close();
            inputStream.close();
            return dbDumpFilePath + dbDumpFilename;

        } catch (IOException ex) {
            LOGGER.error("Downloading db dump failed", ex);
            return null;
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ex) {
                LOGGER.error("Closing FTP connection failed");
                return null;
            }
        }
    }
}
