package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.repository.AirspaceActivityRepository;
import com.alexsoft.smarthouse.entity.*;
import com.alexsoft.smarthouse.model.airplaneslive.Aircraft;
import com.alexsoft.smarthouse.model.airplaneslive.AircraftData;
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
import com.alexsoft.smarthouse.utils.TempUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
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

import static com.alexsoft.smarthouse.utils.DateUtils.toLocalDateTimeAtZone;
import static com.alexsoft.smarthouse.utils.DateUtils.toUtc;

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
    @Value("${mqtt.topic}")
    private String measurementTopic;

    private final MetarLocationsConfig metarLocationsConfig;
    private final RestTemplate restTemplate;
    private final IndicationService indicationService;
    private final TempUtils tempUtils = new TempUtils();
    private final AirspaceActivityRepository airspaceActivityRepository;
    private final MessageSenderService messageSenderService;

    // WeatherAPI configuration
    @Value("${weatherapi.baseUri:https://api.weatherapi.com/v1/forecast.json}")
    private String weatherApiBaseUri;

//    @Value("${weatherapi.key:}")
    private String weatherApiKey = "d6f0c7e886914244b47212927261601";

    // Location to query (zip/city). Example: 33019
    @Value("${weatherapi.q:33019}")
    private String weatherApiQuery;

    // number of forecast days to request (we need at least 2 to get tomorrow)
    @Value("${weatherapi.days:2}")
    private Integer weatherApiDays;

    // schedule for WeatherAPI call (default: every hour at minute 0)
    @Value("${weatherapi.cron:0 0 * * * *}")
    private String weatherApiCron;


    public MetarService(MetarLocationsConfig metarLocationsConfig, RestTemplateBuilder restTemplateBuilder, IndicationService indicationService,
            AirspaceActivityRepository airspaceActivityRepository, MessageSenderService messageSenderService) {
        this.metarLocationsConfig = metarLocationsConfig;
        this.restTemplate = restTemplateBuilder.build();
        this.indicationService = indicationService;
        this.airspaceActivityRepository = airspaceActivityRepository;
        this.messageSenderService = messageSenderService;
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
                    indication.setReceivedLocal(toLocalDateTimeAtZone(indication.getReceivedUtc(), timeZone));
                    IndicationV2 indicationV2 = toIndicationV2(indication);
                    try {
                        indicationV2.setWindSpeed(new Measurement(metar.getWindSpeed().getValue(), null, null));
                        indicationV2.setWindDirection(new Measurement(metar.getWindDirection().getValue(), null, null));
                    } catch (Exception e) {
                        LOGGER.error("Error during setting wind speed and wind direction", e);
                    }
                    indicationService.save(indication, null);
                } else {
                    LOGGER.info("Metar is expired: {}", metar);
                }

            } catch (Exception e) {
                LOGGER.error("Error during retrieving and processing metar data", e);
            }
        });
    }

    // Calls WeatherAPI every hour, saves current conditions and forecast for the same hour tomorrow
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
                    .queryParam("days", 4);

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

            // Common location data
            String place = optText(root.at("/location/name"));
            String tzId = optText(root.at("/location/tz_id"));

            // Forecast for 1 and 3 days in advance
            com.fasterxml.jackson.databind.JsonNode forecastDays = root.path("forecast").path("forecastday");
            if (forecastDays.isArray() && forecastDays.size() > 0) {
                // Forecast for the same hour tomorrow
                Indication forecastInd1 = indicationFromWeatherApiForecast(forecastDays, place, tzId, 1);
                if (forecastInd1 != null) {
                    indicationService.save(forecastInd1, null);
                }
                // Forecast for the same hour 3 days later
                Indication forecastInd3 = indicationFromWeatherApiForecast(forecastDays, place, tzId, 3);
                if (forecastInd3 != null) {
                    indicationService.save(forecastInd3, null);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error during retrieving and processing WeatherAPI data", e);
        }
    }

    private String optText(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private Indication indicationFromWeatherApiForecast(com.fasterxml.jackson.databind.JsonNode forecastDays,
                                                        String place, String tzId, int daysInAdvance) {
        try {
            // Determine target local hour
            ZoneId zone = tzId != null ? ZoneId.of(tzId) : ZoneId.of("UTC");
            ZonedDateTime nowAtZone = ZonedDateTime.now(zone);
            int targetHour = nowAtZone.getHour();
            ZonedDateTime targetTime = nowAtZone.plusDays(daysInAdvance).withHour(targetHour).withMinute(0).withSecond(0).withNano(0);

            // Flatten all hourly entries and find matching hour
            com.fasterxml.jackson.databind.JsonNode bestHourNode = null;
            for (com.fasterxml.jackson.databind.JsonNode day : forecastDays) {
                for (com.fasterxml.jackson.databind.JsonNode hour : day.path("hour")) {
                    String timeStr = optText(hour.path("time")); // yyyy-MM-dd HH:mm in local time zone
                    if (timeStr == null) continue;
                    try {
                        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                        LocalDateTime ldt = LocalDateTime.parse(timeStr, f);
                        ZonedDateTime zdt = ldt.atZone(zone);
                        if (zdt.isEqual(targetTime)) {
                            bestHourNode = hour;
                            break;
                        }
                    } catch (Exception ignore) {}
                }
                if (bestHourNode != null) break;
            }

            if (bestHourNode == null) {
                // Fallback: take the matching hour from the specific day index if exists
                if (forecastDays.size() > daysInAdvance) {
                    com.fasterxml.jackson.databind.JsonNode targetDay = forecastDays.get(daysInAdvance);
                    if (targetDay != null && targetDay.path("hour").isArray() && targetDay.path("hour").size() > targetHour) {
                        bestHourNode = targetDay.path("hour").get(targetHour);
                    }
                }
            }

            if (bestHourNode == null) return null;

            Double tempC = asDouble(bestHourNode.path("temp_c"), null);
            Integer humidity = asInt(bestHourNode.path("humidity"), null);
            Double dewpointC = asDouble(bestHourNode.path("dewpoint_c"), null);
            Integer windDeg = asInt(bestHourNode.path("wind_degree"), null);
            Double windKph = asDouble(bestHourNode.path("wind_kph"), null);
            String timeStr = optText(bestHourNode.path("time"));

            LocalDateTime issuedLocal = null;
            if (timeStr != null) {
                try {
                    java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                    issuedLocal = LocalDateTime.parse(timeStr, f);
                } catch (Exception ignore) {}
            }

            LocalDateTime nowUtc = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();

            Indication indication = new Indication();
            indication.setAggregationPeriod(AggregationPeriod.INSTANT);
            indication.setIndicationPlace("FORT-LAUDERDALE-" + daysInAdvance + "D-FRCST");
            indication.setInOut(InOut.OUT);
            indication.setIssued(issuedLocal != null ? issuedLocal : nowUtc);
            indication.setReceivedUtc(toUtc(issuedLocal));
            indication.setPublisherId("weatherapi.com");

            if (tzId != null) {
                indication.setReceivedLocal(issuedLocal);
            }

            Air air = new Air();
            indication.setAir(air);
            Temp temp = new Temp();
            air.setTemp(temp);
            if (tempC != null) temp.setCelsius(tempC);
            if (humidity != null) temp.setRh(humidity);
            if (tempC != null && humidity != null) {
                temp.setAh(tempUtils.calculateAbsoluteHumidity(tempC.floatValue(), humidity).doubleValue());
            }

            try {
                Integer windMs = null;
                if (windKph != null) {
                    windMs = (int) BigDecimal.valueOf(windKph / 3.6d).setScale(2, RoundingMode.HALF_UP).doubleValue();
                }
                air.setWind(new Wind(null, windDeg, windMs));
            } catch (Exception e) {
                LOGGER.error("Error during setting wind from WeatherAPI forecast", e);
            }

            return indication;
        } catch (Exception e) {
            LOGGER.error("Error mapping WeatherAPI forecast to Indication", e);
            return null;
        }
    }

    private Double asDouble(com.fasterxml.jackson.databind.JsonNode node, Double dflt) {
        return (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) ? dflt : node.asDouble();
    }

    private Integer asInt(com.fasterxml.jackson.databind.JsonNode node, Integer dflt) {
        return (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToInt()) ? dflt : node.asInt();
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
        indication.setPublisherId("AVWX");
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
            LOGGER.warn("Couldn't read metar: {}",  e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Couldn't read metar: {}",  e.getMessage());
        }
        return metar;
    }

    public void readAircraftNumber() {

        List<Aircraft> aircrafts = new ArrayList<>();
        String baseUrl = "https://api.airplanes.live/v2/point/YOUR_LAT/YOUR_LON/15";
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
            LOGGER.warn("Couldn't readAircraftNumber: {}",  e.getMessage());
            return;
        }

        // Log raw payload
        rawPayload = response.getBody();
        if (!rawPayload.equalsIgnoreCase("[]")) {
            // Parse raw payload into FlightData object
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

        // Save AirspaceActivity to the repository
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
