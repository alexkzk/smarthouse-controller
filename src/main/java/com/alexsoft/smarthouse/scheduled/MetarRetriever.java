package com.alexsoft.smarthouse.scheduled;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.alexsoft.smarthouse.db.entity.Air;
import com.alexsoft.smarthouse.db.entity.HouseState;
import com.alexsoft.smarthouse.db.entity.Temp;
import com.alexsoft.smarthouse.db.repository.HouseStateRepository;
import com.alexsoft.smarthouse.model.metar.Metar;
import com.alexsoft.smarthouse.service.MetarReceiver;
import com.alexsoft.smarthouse.utils.TempUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetarRetriever {

    private final HouseStateRepository houseStateRepository;
    private final MetarReceiver metarReceiver;
    private final TempUtils tempUtils = new TempUtils();

    @Scheduled(cron = "0 */5 * * * *")
    public void getMetar() {
        Metar metar = metarReceiver.getMetar();
        if(metarIsNotExpired(metar)) {
            Float temp = Float.valueOf(metar.getTemperature().getValue());
            Integer devpoint = metar.getDewpoint().getValue();
            Integer rh = tempUtils.calculateRelativeHumidity(temp, Float.valueOf(devpoint));
            LocalDateTime now = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();
            HouseState houseState = new HouseState();
            houseState.setMeasurePlace("UKLN");
            houseState.setInOut(InOut.OUT);
            houseState.setMessageIssued(now);
            houseState.setMessageReceived(now);
            houseState.setPublisherId("UKLN");
            Air air = new Air();
            houseState.setAir(air);
            Temp temp1 = new Temp();
            air.setTemp(temp1);
            temp1.setCelsius(temp.doubleValue());
            temp1.setRh(rh);
            temp1.setAh(tempUtils.calculateAbsoluteHumidity(temp, rh).doubleValue());
            houseStateRepository.save(houseState);
        }
    }

    public static boolean metarIsNotExpired(final Metar metar) {
        return metar != null && metar.getTime() != null && metar.getTime().getIssueDateTime() != null &&
            ChronoUnit.HOURS.between(metar.getTime().getIssueDateTime(), ZonedDateTime.now()) < 1;
    }
}
