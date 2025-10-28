package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.IndicationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class SummaryThymeleafController {

    private final IndicationService indicationService;

    @GetMapping("/v2/summary")
    public String aggregateV2(Model model, @RequestParam(required = false) String place, @RequestParam(required = false) String period, HttpServletRequest request) {
        ChartDto chartDto;
        if (StringUtils.isBlank(place) && StringUtils.isBlank(period)) {
            chartDto = indicationService.getAggregatedDataV2(request.getRemoteAddr(), request.getServletPath());
        } else {
            chartDto = indicationService.getAggregatedDataDaily(place, period, request.getRemoteAddr(), request.getServletPath());
        }
        model.addAttribute(chartDto);
        return "status/summary";
    }

    @GetMapping("/v3/summary")
    public String aggregateV3(Model model, @RequestParam(required = false) String place, @RequestParam(required = false) String locations,
            @RequestParam(required = false) String period, HttpServletRequest request) {
        ChartDto chartDto = indicationService.getAggregatedDataV3(locations == null ? place : locations, period, request.getRemoteAddr(),
                request.getServletPath());
        model.addAttribute(chartDto);
        return "status/summary";
    }
}
