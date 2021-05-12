package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.model.metar.Metar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MetarReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetarReceiver.class);
    private final RestTemplate restTemplate;

    @Value("${avwx.token}")
    private String avwxToken;

    @Value("${avwx.baseUri}")
    private String baseUri;

    @Value("${avwx.metarSubUri}")
    private String metarSubUri;

    public MetarReceiver(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
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
