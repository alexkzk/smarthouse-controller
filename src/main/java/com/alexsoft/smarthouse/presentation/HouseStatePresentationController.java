package com.alexsoft.smarthouse.presentation;

import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.IndicationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping
@RequiredArgsConstructor
public class HouseStatePresentationController {

    private final IndicationService indicationService;

    @GetMapping("/summary")
    public String aggregate(Model model) {
        ChartDto chartDto = indicationService.getAggregatedData();
        model.addAttribute(chartDto);
        return "status/summary";
    }

    @GetMapping("/v2/summary")
    public String aggregateV2(Model model, @RequestParam(required = false) String place, @RequestParam(required = false) String period) {
        ChartDto chartDto;
        if (StringUtils.isBlank(place) && StringUtils.isBlank(period)) {
            chartDto = indicationService.getAggregatedDataV2();
        } else {
            chartDto = indicationService.getAggregatedDataDaily(place, period);
        }
        model.addAttribute(chartDto);
        return "status/summary";
    }
}
