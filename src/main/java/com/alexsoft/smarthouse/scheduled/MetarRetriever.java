package com.alexsoft.smarthouse.scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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

import static com.alexsoft.smarthouse.utils.Constants.MIAMI_MEASURE_PLACE;
import static com.alexsoft.smarthouse.utils.Constants.SEATTLE_MEASURE_PLACE;

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

    private final RestTemplate restTemplate;
    private final IndicationService indicationService;
    private final TempUtils tempUtils = new TempUtils();

    public MetarRetriever(RestTemplateBuilder restTemplateBuilder, IndicationService indicationService) {
        this.restTemplate = restTemplateBuilder.build();
        this.indicationService = indicationService;
    }

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getChernivtsiMetar() {
        Metar metar = getMetar(chernivtsiIcao);
        if (metarIsNotExpired(metar)) {
            Indication indication = houseStateFromMetar(metar);
            indicationService.save(indication);
            // In order to smooth the 10 minute measurements chart we need to have measurements at least twice per 10 minutes
            // but we cannot afford to ping the remote server so often
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
        }
    }

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getSeattleMetar() {
        Metar metar = getMetar(seattleIcao);
        if (metarIsNotExpired(metar)) {
            Indication indication = houseStateFromMetar(metar);
            indication.setIndicationPlace(SEATTLE_MEASURE_PLACE);
            //  To offset the time in order to compare the weather of the same hours (e.g 8PM in Ukraine and 8PM in the USA)
            indication.setReceived(indication.getReceived().plus(14, ChronoUnit.HOURS));
            indicationService.save(indication);
            // In order to smooth the 10 minute measurements chart we need to have measurements at least twice per 10 minutes
            // but we cannot afford to ping the remote server so often
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
        }
    }

    @Scheduled(cron = "${avwx.metar-receiving-cron}")
    public void getMiamiMetar() {
        Metar metar = getMetar(miamiIcao);
        if (metarIsNotExpired(metar)) {
            Indication indication = houseStateFromMetar(metar);
            indication.setIndicationPlace(MIAMI_MEASURE_PLACE);
            //  To offset the time in order to compare the weather of the same hours (e.g 8PM in Ukraine and 8PM in the USA)
            indication.setReceived(indication.getReceived().plus(17, ChronoUnit.HOURS));
            indicationService.save(indication);
            // In order to smooth the 10 minute measurements chart we need to have measurements at least twice per 10 minutes
            // but we cannot afford to ping the remote server so often
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
            indication.setReceived(indication.getReceived().minus(3, ChronoUnit.MINUTES));
            indicationService.save(indication);
        }
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
        indication.setReceived(now);
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
