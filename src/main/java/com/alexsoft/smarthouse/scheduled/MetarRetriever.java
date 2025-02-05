package com.alexsoft.smarthouse.scheduled;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.db.entity.*;
import com.alexsoft.smarthouse.db.repository.AirspaceActivityRepository;
import com.alexsoft.smarthouse.db.repository.AirspaceRepository;
import com.alexsoft.smarthouse.model.flightradar24.FrAircraft;
import com.alexsoft.smarthouse.model.flightradar24.FlightData;
import com.alexsoft.smarthouse.utils.DateUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.model.avwx.metar.Metar;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.utils.TempUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class MetarRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetarRetriever.class);

    @Value("${avwx.token}")
    private String avwxToken;

    @Value("${avwx.baseUri}")
    private String avwxBaseUri;

    @Value("${flightradar24.baseUri}")
    private String frBaseUri;

    @Value("${flightradar24.token}")
    private String frToken;

    @Value("${avwx.metarSubUri}")
    private String metarSubUri;

    private final MetarLocationsConfig metarLocationsConfig;
    private final RestTemplate restTemplate;
    private final IndicationService indicationService;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;
    private final AirspaceRepository airspaceRepository;
    private final AirspaceActivityRepository airspaceActivityRepository;

    public MetarRetriever(MetarLocationsConfig metarLocationsConfig, RestTemplateBuilder restTemplateBuilder, IndicationService indicationService, DateUtils dateUtils,
            AirspaceRepository airspaceRepository, AirspaceActivityRepository airspaceActivityRepository) {
        this.metarLocationsConfig = metarLocationsConfig;
        this.restTemplate = restTemplateBuilder.build();
        this.indicationService = indicationService;
        this.dateUtils = dateUtils;
        this.airspaceRepository = airspaceRepository;
        this.airspaceActivityRepository = airspaceActivityRepository;
    }

    @Scheduled(cron = "0 0 */1 * * *")
    public void aggregateHourly(){
        LOGGER.info("Hourly aggregating");
        indicationService.createAverageMeasurement(1, ChronoUnit.HOURS);
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void aggregateMinutely(){
        LOGGER.info("Minutely aggregating");
        indicationService.createAverageMeasurement(5, ChronoUnit.MINUTES);
    }

    @Scheduled(cron = "0 0 22 * * *")
    public void aggregateDaily(){
        LOGGER.info("Daily aggregating");
        indicationService.createAverageMeasurement(1, ChronoUnit.DAYS);
    }

    @Scheduled(cron = "0 0 0 1 * *")
    public void aggregateMonthly(){
        LOGGER.info("Monthly aggregating");
        indicationService.createAverageMeasurement(1, ChronoUnit.MONTHS);
    }



    @Scheduled(cron = "${flightradar24.aircraft-reading-cron}")
    public void retrieveAndProcessAircraftNumber() {
        readAircraftNumber();
    }


    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void retrieveAndProcessMetarData() {

        metarLocationsConfig.getLocationMapping().forEach((key, value) -> {

            try {
                Metar metar = readMetar(value.keySet().stream().findFirst().get());

                if (metarIsNotExpired(metar)) {
                    Indication indication = toIndication(metar);
                    indication.setIndicationPlace(key);
                    indication.setReceivedUtc(indication.getReceivedUtc());
                    Optional<String> timeZone = value.values().stream().findFirst();
                    indication.setReceivedLocal(dateUtils.toLocalDateTimeAtZone(indication.getReceivedUtc(), timeZone));
                    IndicationV2 indicationV2 = toIndicationV2(indication);
                    try {
                        indicationV2.setWindSpeed(new Measurement(metar.getWindSpeed().getValue(), null, null));
                        indicationV2.setWindDirection(new Measurement(metar.getWindDirection().getValue(), null, null));
                    } catch (Exception e) {
                        LOGGER.error("Error during setting wind speed and wind direction", e);
                    }
                    indicationService.save(indication, indicationV2, true, AggregationPeriod.INSTANT);
                } else {
                    LOGGER.info("Metar is expired: {}", metar);
                }

            } catch (Exception e) {
                LOGGER.error("Error during retrieving and processing metar data", e);
            }
        });
    }

    private Indication toIndication(Metar metar) {
        Float temp = Float.valueOf(metar.getTemperature().getValue());
        Integer devpoint = metar.getDewpoint().getValue();
        Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
        LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Indication indication = new Indication();
        indication.setAggregationPeriod(AggregationPeriod.INSTANT);
        indication.setIndicationPlace(metar.getStation());
        indication.setInOut(InOut.OUT);
        indication.setIssued(metar.getTime().getIssueDateTime().toLocalDateTime());
        indication.setReceivedUtc(now);
        indication.setPublisherId(metar.getStation());
        indication.setMetar(metar.getRaw());
        Air air = new Air();
        indication.setAir(air);
        Temp temp1 = new Temp();
        air.setTemp(temp1);
        temp1.setCelsius(temp.doubleValue());
        temp1.setRh(rh);
        temp1.setAh(tempUtils.calculateAbsoluteHumidity(temp, rh).doubleValue());
        try {
            air.setWind(new Wind(null, metar.getWindDirection().getValue(),
                    ((int) BigDecimal.valueOf(metar.getWindSpeed().getValue() * 0.514444).setScale(2, RoundingMode.HALF_UP).doubleValue())));
        } catch (Exception e) {
            LOGGER.error("Error during setting wind speed and wind direction", e);
        }
        return indication;
    }

    public static boolean metarIsNotExpired(final Metar metar) {
        return metar != null && metar.getTime() != null && metar.getTime().getIssueDateTime() != null &&
            ChronoUnit.HOURS.between(metar.getTime().getIssueDateTime(), ZonedDateTime.now()) < 1;
    }

    public Metar readMetar(String icao) {
        String url = avwxBaseUri + metarSubUri + "&token=" + avwxToken;
        url = url.replace("{ICAO}", icao);
        Metar metar = null;
        try {
            metar = this.restTemplate.getForObject(url, Metar.class);
        } catch (HttpClientErrorException e) {
            // TODO just return null in this case and not to check on the metar's expirity the URL should be changed to onfail=error
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return metar;
    }

    public void readAircraftNumber() {
        ZoneId easternTimeZone = ZoneId.of("America/New_York");
        int currentHour = ZonedDateTime.now(easternTimeZone).getHour();

        Map<String, List<Airspace>> airspaceGroups = airspaceRepository.findAll().stream()
                .filter(airspace -> airspace.getHourFrom() != null && airspace.getHourTo() != null)
                .filter(airspace -> currentHour >= airspace.getHourFrom() && currentHour < airspace.getHourTo())
                .collect(Collectors.groupingBy(Airspace::getName));

        for (Map.Entry<String, List<Airspace>> entry : airspaceGroups.entrySet()) {
            String name = entry.getKey();
            List<Airspace> airspaces = entry.getValue();

            List<FrAircraft> frAircrafts = new ArrayList<>();

            // Process each airspace within the group
            for (Airspace airspace : airspaces) {
                String baseUrl = frBaseUri + "/api/live/flight-positions/full";
                UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl);

                // Add query params from Airspace
                for (Map.Entry<String, String> param : airspace.getParams().entrySet()) {
                    uriBuilder.queryParam(param.getKey(), param.getValue());
                }
                String urlWithParams = uriBuilder.build().toUriString();

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + frToken);
                headers.set("Accept-Version", "v1");
                HttpEntity<String> entity = new HttpEntity<>(headers);

                String rawPayload = null;

                try {
                    // Retrieve raw response as String
                    ResponseEntity<String> exchange = restTemplate.exchange(
                            urlWithParams, HttpMethod.GET, entity, String.class
                    );
                    LOGGER.info("FlightRadar24 request: " + urlWithParams);

                    // Log raw payload
                    rawPayload = exchange.getBody();
                    if (!rawPayload.equalsIgnoreCase("[]")) {
                        // Parse raw payload into FlightData object
                        FlightData flightData = new ObjectMapper().readValue(rawPayload, FlightData.class);
                        if (flightData != null && CollectionUtils.isNotEmpty(flightData.getFrAircrafts())) {
                            frAircrafts.addAll(flightData.getFrAircrafts());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error fetching data for airspace '{}': payload: {}", name, rawPayload, e);
                }
            }

            // Save AirspaceActivity to the repository
            String aircraftsData = frAircrafts.stream()
                    .map(FrAircraft::getCallsign)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.joining(","));

            AirspaceActivity activity = AirspaceActivity.builder()
                    .airspace(name)
                    .airborneAircrafts(frAircrafts.size())
                    .aircrafts(aircraftsData)
                    .timestamp(LocalDateTime.now())
                    .build();

            airspaceActivityRepository.save(activity);
        }
    }

    public static IndicationV2 toIndicationV2(Indication indication) {
        try {
            IndicationV2 indicationV2 = new IndicationV2();
            indicationV2.setIndicationPlace(indication.getIndicationPlace());
            indicationV2.setLocalTime(indication.getReceivedLocal());
            indicationV2.setUtcTime(indication.getReceivedUtc());
            indicationV2.setAggregationPeriod("INSTANT");
            indicationV2.setPublisherId(indication.getPublisherId());
            indicationV2.setInOut(indication.getInOut().name());
            indicationV2.setMetar(indication.getMetar());
            if (indication.getAir() != null) {
                if (indication.getAir().getTemp() != null) {
                    indicationV2.getTemperature().setValue(indication.getAir().getTemp().getCelsius());
                    indicationV2.getRelativeHumidity().setValue(indication.getAir().getTemp().getRh());
                    indicationV2.getAbsoluteHumidity().setValue(indication.getAir().getTemp().getAh());
                }
                if (indication.getAir().getPressure() != null) {
                    indicationV2.getPressure().setValue(indication.getAir().getPressure().getMmHg());
                }
            }
            return indicationV2;

        } catch (Exception e) {
            LOGGER.error("Error during toIndicationV2", e);
            return null;
        }
    }
}
