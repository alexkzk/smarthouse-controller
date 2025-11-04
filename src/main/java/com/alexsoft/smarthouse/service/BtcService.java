package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.*;
import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.alexsoft.smarthouse.repository.BtcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class BtcService {

    private final WebClient webClient = WebClient.create("https://api.coingecko.com/api/v3");

    private final BtcRepository btcRepository;
    private final IndicationService indicationService;

    @Scheduled(fixedRate = 60000)
    public void getBtcRate() {
        try {
            Double btcPrice = webClient.get()
                    .uri("/simple/price?ids=bitcoin&vs_currencies=usd")
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseBtcPrice)
                    .block();

            if (btcPrice != null) {
                LocalDateTime utcTimestamp = Instant.now().atZone(ZoneId.of("UTC")).toLocalDateTime();
                LocalDateTime etTimestamp = utcTimestamp.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("America/New_York")).toLocalDateTime();
                Btc btc = Btc.builder().timestampUtc(utcTimestamp).timestampEt(etTimestamp).price(btcPrice).build();
                Indication indication = Indication.builder().inOut(InOut.OUT).indicationPlace("BTC").aggregationPeriod(AggregationPeriod.INSTANT).issued(etTimestamp)
                        .publisherId("coingecko.com").receivedUtc(utcTimestamp).receivedLocal(etTimestamp).air(Air.builder().temp(Temp.builder().celsius(btcPrice)
                                .build()).build()).build();
                indicationService.save(indication, null);
                btcRepository.save(btc);
            }

        } catch (Exception ex) {
           log.error("Error fetching BTC rate: " + ex.getMessage());
        }
    }


    private Double parseBtcPrice(String response) {
        try {
            String prefix = "\"usd\":";
            int startIndex = response.indexOf(prefix) + prefix.length();
            int endIndex = response.indexOf("}", startIndex);
            double btcPrice = Double.parseDouble(response.substring(startIndex, endIndex));
            log.info("BTC price: {}", btcPrice);
            return btcPrice;
        } catch (Exception ex) {
            log.error("Error parsing BTC price: " + ex.getMessage(), ex);
            return null;
        }
    }


}
