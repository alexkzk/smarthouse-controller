package com.alexsoft.smarthouse.presentation;

import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.IndicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/status")
@RequiredArgsConstructor
public class HouseStatePresentationController {

    private final IndicationService indicationService;

    @GetMapping("/summary")
    public String aggregate(Model model) {
        ChartDto chartDto = indicationService.getAggregatedData();
        model.addAttribute(chartDto);
        return "status/summary";
    }
}
