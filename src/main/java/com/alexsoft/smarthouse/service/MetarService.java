package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.entity.AirspaceActivity;
import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.model.airplaneslive.Aircraft;
import com.alexsoft.smarthouse.model.airplaneslive.AircraftData;
import com.alexsoft.smarthouse.model.avwx.metar.Metar;
import com.alexsoft.smarthouse.repository.AirspaceActivityRepository;
import com.alexsoft.smarthouse.util.TempUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.alexsoft.smarthouse.util.DateUtils.toLocalDateTimeAtZone;
import static com.alexsoft.smarthouse.util.DateUtils.toUtc;

@Service
public class MetarService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetarService.class);

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
    private final IndicationServiceV3 indicationServiceV3;
    private final TempUtils tempUtils = new TempUtils();
    private final AirspaceActivityRepository airspaceActivityRepository;
    private final MessageSenderService messageSenderService;
    private final Executor ioTaskExecutor;

    @Value("${weatherapi.baseUri:https://api.weatherapi.com/v1/forecast.json}")
    private String weatherApiBaseUri;

    private String weatherApiKey = "de3a66a2e64340ab85c31038260102";

    @Value("${weatherapi.q:33019}")
    private String weatherApiQuery;

    @Value("${weatherapi.days:14}")
    private Integer weatherApiDays;

    @Value("${weatherapi.cron:0 0 * * * *}")
    private String weatherApiCron;

    public MetarService(MetarLocationsConfig metarLocationsConfig, RestTemplateBuilder restTemplateBuilder,
                        IndicationServiceV3 indicationServiceV3,
                        AirspaceActivityRepository airspaceActivityRepository, MessageSenderService messageSenderService,
                        @Qualifier("ioTaskExecutor") Executor ioTaskExecutor) {
        this.metarLocationsConfig = metarLocationsConfig;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
        this.indicationServiceV3 = indicationServiceV3;
        this.airspaceActivityRepository = airspaceActivityRepository;
        this.messageSenderService = messageSenderService;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    @Async("ioTaskExecutor")
    @Scheduled(cron = "${flightradar24.aircraft-reading-cron}")
    public void retrieveAndProcessAircraftNumber() {
        try {
            readAircraftNumber();
        } catch (Exception e) {
            LOGGER.error("Error during retrieveAndProcessAircraftNumber", e);
        }
    }

    @Async("ioTaskExecutor")
    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void retrieveAndProcessMetarData() {
        List<CompletableFuture<Void>> futures = metarLocationsConfig.getLocationMapping().entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    String key = entry.getKey();
                    Map<String, String> value = entry.getValue();
                    try {
                        Metar metar = readMetar(value.keySet().stream().findFirst().get());
                        if (metarIsNotExpired(metar)) {
                            Float temp = Float.valueOf(metar.getTemperature().getValue());
                            Integer dewpoint = metar.getDewpoint().getValue();
                            Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(dewpoint));
                            Double ah = tempUtils.calculateAbsoluteHumidity(temp, rh).doubleValue();
                            LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
                            Optional<String> timeZone = value.values().stream().findFirst();
                            LocalDateTime localTime = toLocalDateTimeAtZone(now, timeZone);
                            IndicationV3.IndicationV3Builder builder = IndicationV3.builder()
                                    .publisherId("AVWX").utcTime(now).localTime(localTime).locationId(key);
                            indicationServiceV3.saveAll(List.of(
                                    builder.measurementType("temp").unit("c").value(temp.doubleValue()).build(),
                                    builder.measurementType("rh").unit("%").value(rh.doubleValue()).build(),
                                    builder.measurementType("ah").unit("g/m3").value(ah).build()
                            ));
                        } else {
                            LOGGER.info("Metar is expired: {}", metar);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error during retrieving and processing metar data for {}", key, e);
                    }
                }, ioTaskExecutor)).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Async("ioTaskExecutor")
    @Scheduled(cron = "0 0 */1 * * *")
    public void retrieveAndProcessWeatherApi() {
        if (weatherApiKey == null || weatherApiKey.isBlank()) {
            LOGGER.warn("WeatherAPI key is not configured; skipping call");
            return;
        }

        try {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(weatherApiBaseUri)
                    .queryParam("key", weatherApiKey)
                    .queryParam("q", weatherApiQuery)
                    .queryParam("days", weatherApiDays);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.ACCEPT, "application/json");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uriBuilder.toUriString(), HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                LOGGER.warn("WeatherAPI response is not successful: {}", response.getStatusCode());
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(response.getBody());

            String place = optText(root.at("/location/name"));
            String tzId = optText(root.at("/location/tz_id"));

            com.fasterxml.jackson.databind.JsonNode forecastDays = root.path("forecast").path("forecastday");
            if (forecastDays.isArray() && forecastDays.size() > 0) {
                for (int days : new int[]{1, 3, 7, 13}) {
                    List<IndicationV3> forecastList = indicationV3sFromWeatherApiForecast(forecastDays, place, tzId, days);
                    if (!forecastList.isEmpty()) {
                        indicationServiceV3.saveAll(forecastList);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during retrieving and processing WeatherAPI data", e);
        }
    }

    private String optText(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private List<IndicationV3> indicationV3sFromWeatherApiForecast(com.fasterxml.jackson.databind.JsonNode forecastDays,
                                                                   String place, String tzId, int daysInAdvance) {
        try {
            ZoneId zone = tzId != null ? ZoneId.of(tzId) : ZoneId.of("UTC");
            ZonedDateTime nowAtZone = ZonedDateTime.now(zone);
            int targetHour = nowAtZone.getHour();
            ZonedDateTime targetTime = nowAtZone.plusDays(daysInAdvance).withHour(targetHour).withMinute(0).withSecond(0).withNano(0);

            com.fasterxml.jackson.databind.JsonNode bestHourNode = null;
            for (com.fasterxml.jackson.databind.JsonNode day : forecastDays) {
                for (com.fasterxml.jackson.databind.JsonNode hour : day.path("hour")) {
                    String timeStr = optText(hour.path("time"));
                    if (timeStr == null) continue;
                    try {
                        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        LocalDateTime ldt = LocalDateTime.parse(timeStr, f);
                        ZonedDateTime zdt = ldt.atZone(zone);
                        if (zdt.isEqual(targetTime)) {
                            bestHourNode = hour;
                            break;
                        }
                    } catch (Exception ignore) {
                    }
                }
                if (bestHourNode != null) break;
            }

            if (bestHourNode == null && forecastDays.size() > daysInAdvance) {
                com.fasterxml.jackson.databind.JsonNode targetDay = forecastDays.get(daysInAdvance);
                if (targetDay != null && targetDay.path("hour").isArray() && targetDay.path("hour").size() > targetHour) {
                    bestHourNode = targetDay.path("hour").get(targetHour);
                }
            }

            if (bestHourNode == null) return Collections.emptyList();

            Double tempC = asDouble(bestHourNode.path("temp_c"), null);
            Integer humidity = asInt(bestHourNode.path("humidity"), null);
            String timeStr = optText(bestHourNode.path("time"));

            LocalDateTime issuedLocal = null;
            if (timeStr != null) {
                try {
                    java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    issuedLocal = LocalDateTime.parse(timeStr, f);
                } catch (Exception ignore) {
                }
            }

            LocalDateTime utcTime = issuedLocal != null ? toUtc(issuedLocal) : ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
            String locationId = "FORT-LAUDERDALE-" + daysInAdvance + "D-FRCST";

            List<IndicationV3> result = new ArrayList<>();
            IndicationV3.IndicationV3Builder builder = IndicationV3.builder()
                    .publisherId("weatherapi.com").locationId(locationId).utcTime(utcTime).localTime(issuedLocal);

            if (tempC != null) {
                result.add(builder.measurementType("temp").unit("c").value(tempC).build());
            }
            if (humidity != null) {
                result.add(builder.measurementType("rh").unit("%").value(humidity.doubleValue()).build());
            }
            if (tempC != null && humidity != null) {
                result.add(builder.measurementType("ah").unit("g/m3")
                        .value(tempUtils.calculateAbsoluteHumidity(tempC.floatValue(), humidity).doubleValue()).build());
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Error mapping WeatherAPI forecast to IndicationV3", e);
            return Collections.emptyList();
        }
    }

    private Double asDouble(com.fasterxml.jackson.databind.JsonNode node, Double dflt) {
        return (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) ? dflt : node.asDouble();
    }

    private Integer asInt(com.fasterxml.jackson.databind.JsonNode node, Integer dflt) {
        return (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToInt()) ? dflt : node.asInt();
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
            LOGGER.warn("Couldn't read metar: {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Couldn't read metar: {}", e.getMessage());
        }
        return metar;
    }

    public void readAircraftNumber() {

        List<Aircraft> aircrafts = new ArrayList<>();
        String baseUrl = "https://api.airplanes.live/v2/point/26.197679/-80.172695/15";
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(baseUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + frToken);
        headers.set("Accept-Version", "v1");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String rawPayload = null;
        ResponseEntity<String> response;

        try {
            response = restTemplate.exchange(baseUrl, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            LOGGER.warn("Couldn't readAircraftNumber: {}", e.getMessage());
            return;
        }

        rawPayload = response.getBody();
        if (rawPayload == null) {
            LOGGER.warn("readAircraftNumber: empty response body");
            return;
        }
        if (!rawPayload.equalsIgnoreCase("[]")) {
            AircraftData aircraftData = null;
            try {
                aircraftData = new ObjectMapper().readValue(rawPayload, AircraftData.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            if (aircraftData != null && CollectionUtils.isNotEmpty(aircraftData.getAircrafts())) {
                aircrafts.addAll(aircraftData.getAircrafts());
            }
        }

        aircrafts = aircrafts.stream().filter(Objects::nonNull)
                .filter(aircraft -> NumberUtils.isCreatable(aircraft.getAltBaro()) ? Integer.parseInt(aircraft.getAltBaro()) < 5000 : aircraft.getGroundSpeed() > 40)
                .filter(aircraft -> aircraft.getLatitude() != null && aircraft.getLongitude() != null && aircraft.getGroundSpeed() != null)
                .filter(aircraft -> aircraft.getLatitude() < 26.35 && aircraft.getLatitude() > 26.16)
                .filter(aircraft -> aircraft.getLongitude() > -80.35 && aircraft.getLongitude() < -80.014)
                .filter(aircraft -> aircraft.getGroundSpeed() > 40)
                .collect(Collectors.toList());

        LOGGER.info(aircrafts.size() + " aircrafts in the FXE area: " + aircrafts.stream().map(Aircraft::getRegistration).filter(Objects::nonNull)
                .collect(Collectors.joining(",")));

        String aircraftsData = aircrafts.stream()
                .map(Aircraft::getRegistration)
                .filter(Objects::nonNull)
                .map(String::trim)
                .sorted()
                .collect(Collectors.joining(","));

        AirspaceActivity activity = AirspaceActivity.builder()
                .airspace("FXE")
                .airborneAircrafts(aircrafts.size())
                .aircrafts(aircraftsData)
                .timestamp(LocalDateTime.now())
                .build();

        airspaceActivityRepository.save(activity);
    }
}
