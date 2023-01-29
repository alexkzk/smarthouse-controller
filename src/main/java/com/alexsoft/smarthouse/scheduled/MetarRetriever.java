package com.alexsoft.smarthouse.scheduled;

import com.alexsoft.smarthouse.configuration.MetarLocationsConfig;
import com.alexsoft.smarthouse.utils.DateUtils;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.db.entity.Air;
import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.db.entity.Temp;
import com.alexsoft.smarthouse.model.avwx.metar.Metar;
import com.alexsoft.smarthouse.service.IndicationService;
import com.alexsoft.smarthouse.utils.TempUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class MetarRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetarRetriever.class);

    @Value("${avwx.chernivtsi-icao}")
    private String chernivtsiIcao;

    @Value("${avwx.seattle-icao}")
    private String seattleIcao;

    @Value("${avwx.miami-icao}")
    private String miamiIcao;

    @Value("${avwx.token}")
    private String avwxToken;

    @Value("${avwx.baseUri}")
    private String baseUri;

    @Value("${avwx.metarSubUri}")
    private String metarSubUri;

    private final MetarLocationsConfig metarLocationsConfig;
    private final RestTemplate restTemplate;
    private final IndicationService indicationService;
    private final TempUtils tempUtils = new TempUtils();
    private final DateUtils dateUtils;

    public MetarRetriever(MetarLocationsConfig metarLocationsConfig, RestTemplateBuilder restTemplateBuilder, IndicationService indicationService, DateUtils dateUtils) {
        this.metarLocationsConfig = metarLocationsConfig;
        this.restTemplate = restTemplateBuilder.build();
        this.indicationService = indicationService;
        this.dateUtils = dateUtils;
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

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getSeattleMetar() {

        metarLocationsConfig.getLocationMapping().entrySet().forEach(entry -> {

            Metar metar = getMetar(entry.getValue().keySet().stream().findFirst().get());

            if (metarIsNotExpired(metar)) {

                Indication indication = houseStateFromMetar(metar);
                indication.setIndicationPlace(entry.getKey());
                indication.setReceivedUtc(indication.getReceivedUtc());
                String timeZone = entry.getValue().values().stream().findFirst().get();
                indication.setReceivedLocal(dateUtils.ttoLocalDateTimeAtZone(indication.getReceivedUtc(), timeZone));
                indicationService.save(indication, true, AggregationPeriod.INSTANT);
            } else {
                LOGGER.info("Metar is expired: {}", metar);
            }
        });
    }

    private Indication houseStateFromMetar(Metar metar) {
        Float temp = Float.valueOf(metar.getTemperature().getValue());
        Integer devpoint = metar.getDewpoint().getValue();
        Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
        LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        Indication indication = new Indication();
        indication.setIndicationPlace(metar.getStation());
        indication.setInOut(InOut.OUT);
        indication.setIssued(metar.getTime().getIssueDateTime().toLocalDateTime());
        indication.setReceivedUtc(now);
        indication.setPublisherId(metar.getStation());
        Air air = new Air();
        indication.setAir(air);
        Temp temp1 = new Temp();
        air.setTemp(temp1);
        temp1.setCelsius(temp.doubleValue());
        temp1.setRh(rh);
        temp1.setAh(tempUtils.calculateAbsoluteHumidity(temp, rh).doubleValue());
        return indication;
    }

    public static boolean metarIsNotExpired(final Metar metar) {
        return metar != null && metar.getTime() != null && metar.getTime().getIssueDateTime() != null &&
            ChronoUnit.HOURS.between(metar.getTime().getIssueDateTime(), ZonedDateTime.now()) < 1;
    }

    public Metar getMetar(String icao) {
        String url = baseUri + metarSubUri + "&token=" + avwxToken;
        url = url.replace("{ICAO}", icao);
        Metar forObject = null;
        try {
            forObject = this.restTemplate.getForObject(url, Metar.class);
        } catch (HttpClientErrorException e) {
            // TODO just return null in this case and not to check on the metar's expirity the URL should be changed to onfail=error
            LOGGER.warn(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return forObject;
    }
}
