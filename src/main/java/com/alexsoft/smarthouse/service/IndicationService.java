package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.configuration.SmarthouseConfiguration;
import com.alexsoft.smarthouse.db.entity.*;
import com.alexsoft.smarthouse.db.repository.IndicationRepository;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryCustom;
import com.alexsoft.smarthouse.db.repository.IndicationRepositoryV2;
import com.alexsoft.smarthouse.db.repository.VisitRepository;
import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.scheduled.MetarRetriever;
import com.alexsoft.smarthouse.utils.DateUtils;
import com.alexsoft.smarthouse.utils.TempUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alexsoft.smarthouse.utils.Constants.*;
import static com.alexsoft.smarthouse.utils.MathUtils.doubleToInt;
import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class IndicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndicationService.class);

    public static final Comparator<Object> OBJECT_TO_STRING_COMPARATOR = Comparator.comparing(o -> ((String) o));

    private final IndicationRepository indicationRepository;
    private final IndicationRepositoryV2 indicationRepositoryV2;
    private final IndicationRepositoryCustom indicationRepositoryCustom;
    private final VisitRepository visitRepository;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;
    private final MetarLocationsConfig metarLocationsConfig;
    private final SmarthouseConfiguration smarthouseConfiguration;

    @Value("${mqtt.msgSavingEnabled}")
    private Boolean msgSavingEnabled;

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

    /*@PostConstruct
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
    }*/

    private void logVisit(String remoteAddr, String servletPath) {
        Visit visit = new Visit();
        visit.setTime(LocalDateTime.now());
        visit.setIpAddress(remoteAddr);
        visit.setPath(servletPath);
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
            String place = indication.getIndicationPlace();
            Optional<String> timeZone = Optional.ofNullable(metarLocationsConfig.getLocationMapping().get(place))
                    .map(Map::values).flatMap(values -> values.stream().findFirst());
            indication.setReceivedLocal(dateUtils.toLocalDateTimeAtZone(indication.getReceivedUtc(), timeZone));
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
        Wind averagedWind = null;
        try {
            averagedWind = Wind.builder()
                    .direction(doubleToInt(winds.stream().filter(wind -> wind.getDirection() != null).mapToDouble(Wind::getDirection).average().orElse(Double.NaN)))
                    .speedMs(doubleToInt(winds.stream().filter(wind -> wind.getSpeedMs() != null).mapToDouble(Wind::getDirection).average().orElse(Double.NaN)))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Failed to average wind for {}", indications, e);
        }

        averagedIndication.setAir(Air.builder()
                .quality(quality.isEmpty() ? null : quality)
                .temp(avgTemp.isEmpty() ? null : avgTemp)
                .pressure(avgPressure.isEmpty() ? null : avgPressure)
                .wind(averagedWind == null || averagedWind.isEmpty() ? null : averagedWind)
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

    public void save(Indication indicationToSave, IndicationV2 indicationV2, boolean normalize, AggregationPeriod aggregationPeriod) {
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
            if (indicationV2 != null) {
                indicationRepositoryV2.save(indicationV2);
            }
            Indication savedIndication = indicationRepository.save(indicationToSave);
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

    public ChartDto getAggregatedDataV2(String remoteAddr, String servletPath) {
        logVisit(remoteAddr, servletPath);
        List<Map<String, Object>> aggregates = indicationRepository.getAggregatedHourlyAndMinutely();
        ChartDto chartDto = new ChartDto();
        setTemps(aggregates, chartDto);
        setRhs(aggregates, chartDto);
        setAhs(aggregates, chartDto);
        setAqis(aggregates, chartDto);
        setColors(chartDto);

        return chartDto;
    }

    public ChartDto getAggregatedDataDaily(String place, String period, String remoteAddr, String servletPath) {
        logVisit(remoteAddr, servletPath);
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

    public ChartDto getAggregatedDataV3(String commaSeparatedPlaces, String period, String remoteAddr, String servletPath) {
        logVisit(remoteAddr, servletPath);
        List<String> places = commaSeparatedPlaces == null ? null : Arrays.stream(commaSeparatedPlaces.split(",")).map(String::toUpperCase).toList();
        if (places != null && places.contains("FLORIDA")) {
            places = Stream.concat(
                    places.stream().filter(e -> e.equals("FLORIDA")),
                    Stream.of("MIAMI", "FORT-LAUDERDALE", "ORLANDO", "JACKSONVILLE", "DESTIN", "APT2107S-B", "APT2107S-MB")
            ).collect(Collectors.toList());
        }
        List<Map<String, Object>> aggregates = indicationRepositoryCustom.getAggregatedData(places, period == null ? "" : period.toUpperCase());

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
        Object[] outdoorColors = outdoorTempHeader.stream().map(s -> getColorForPlace((String) s)).toArray();
        chartDto.setOutdoorColors(outdoorColors);

        Object[] rhs = (Object[]) chartDto.getRhs()[0];
        List<Object> rhHeader = Arrays.stream(rhs).collect(toList());
        rhHeader = rhHeader.subList(1, rhHeader.size());
        Object[] rhColors = rhHeader.stream().map(s -> getColorForPlace((String) s)).toArray();
        chartDto.setRhsColors(rhColors);

        Object[] ahs = (Object[]) chartDto.getAhs()[0];
        List<Object> ahHeader = Arrays.stream(ahs).collect(toList());
        ahHeader = ahHeader.subList(1, ahHeader.size());
        Object[] ahColors = ahHeader.stream().map(s -> getColorForPlace((String) s)).toArray();
        chartDto.setAhsColors(ahColors);

        Object[] indoor = (Object[]) chartDto.getIndoorTemps()[0];
        List<Object> indoorHeader = Arrays.stream(indoor).collect(toList());
        indoorHeader = indoorHeader.subList(1, indoorHeader.size());
        Object[] indoorColors = indoorHeader.stream().map(s -> getColorForPlace((String) s)).toArray();
        chartDto.setIndoorColors(indoorColors);

    }

    public String getColorForPlace(String place){
        int i = place.hashCode();
        if (smarthouseConfiguration.getColors() != null) {
            String s = smarthouseConfiguration.getColors().get(place);
            if (StringUtils.isNotBlank(s)) {
                return s;
            }
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


    /*public boolean uploadToFtp() {
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
    }*/

    /*public String downloadFromFtp() {

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
    }*/
}
