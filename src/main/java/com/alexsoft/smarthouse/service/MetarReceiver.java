package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.model.Metar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class MetarReceiver {

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

    public Metar getMetar() {
        String url = baseUri + metarSubUri + "&token=" + avwxToken;
        return this.restTemplate.getForObject(url, Metar.class);
    }
}
