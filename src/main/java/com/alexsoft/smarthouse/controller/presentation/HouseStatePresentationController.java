package com.alexsoft.smarthouse.controller.presentation;

import java.util.List;

import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@AllArgsConstructor
public class HouseStatePresentationController {

    private final HouseStateService houseStateService;

    @GetMapping("/average")
    public String averagedMeasures(Model model) {
        List<HouseStateDto> hourlyList = houseStateService.hourlyAggregatedMeasures();
        List<HouseStateDto> minutelyList = houseStateService.minutelyAggregatedMeasures();
        model.addAttribute("hstates", hourlyList);
        model.addAttribute("mstates", minutelyList);
        return "average";
    }

}
