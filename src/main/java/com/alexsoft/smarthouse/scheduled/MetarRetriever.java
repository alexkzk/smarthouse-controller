package com.alexsoft.smarthouse.scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.alexsoft.smarthouse.db.entity.Air;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Temp;
import com.alexsoft.smarthouse.model.metar.Metar;
import com.alexsoft.smarthouse.service.HouseStateService;
import com.alexsoft.smarthouse.utils.TempUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import static com.alexsoft.smarthouse.utils.Constants.SEATTLE_MEASURE_PLACE;

@Service
public class MetarRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetarRetriever.class);

    @Value("${avwx.chernivtsi-icao}")
    private String chernivtsiIcao;

    @Value("${avwx.seattle-icao}")
    private String seattleIcao;

    @Value("${avwx.token}")
    private String avwxToken;

    @Value("${avwx.baseUri}")
    private String baseUri;

    @Value("${avwx.metarSubUri}")
    private String metarSubUri;

    private final RestTemplate restTemplate;
    private final HouseStateService houseStateService;
    private final TempUtils tempUtils = new TempUtils();

    public MetarRetriever(RestTemplateBuilder restTemplateBuilder, HouseStateService houseStateService) {
        this.restTemplate = restTemplateBuilder.build();
        this.houseStateService = houseStateService;
    }

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getChernivtsiMetar() {
        Metar metar = getMetar(chernivtsiIcao);
        if (metarIsNotExpired(metar)) {
            HouseState houseState = houseStateFromMetar(metar);
            houseStateService.save(houseState);
        }
    }

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getSeattleMetar() {
        Metar metar = getMetar(seattleIcao);
        if (metarIsNotExpired(metar)) {
            HouseState houseState = houseStateFromMetar(metar);
            houseState.setMeasurePlace(SEATTLE_MEASURE_PLACE);
            //  To offset the time in order to compare the weather of the same hours (e.g 8PM in Ukraine and 8PM in the USA)
            houseState.setMessageReceived(houseState.getMessageReceived().plus(14, ChronoUnit.HOURS));
            houseStateService.save(houseState);
        }
    }

    private HouseState houseStateFromMetar(Metar metar) {
        Float temp = Float.valueOf(metar.getTemperature().getValue());
        Integer devpoint = metar.getDewpoint().getValue();
        Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
        LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
        HouseState houseState = new HouseState();
        houseState.setMeasurePlace(metar.getStation());
        houseState.setInOut(InOut.OUT);
        houseState.setMessageIssued(metar.getTime().getIssueDateTime().toLocalDateTime());
        houseState.setMessageReceived(now);
        houseState.setPublisherId(metar.getStation());
        Air air = new Air();
        houseState.setAir(air);
        Temp temp1 = new Temp();
        air.setTemp(temp1);
        temp1.setCelsius(temp.doubleValue());
        temp1.setRh(rh);
        temp1.setAh(tempUtils.calculateAbsoluteHumidity(temp, rh).doubleValue());
        return houseState;
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
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return forObject;
    }
}
