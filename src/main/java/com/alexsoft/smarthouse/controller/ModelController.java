package com.alexsoft.smarthouse.controller;

import java.util.List;

import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@AllArgsConstructor
public class ModelController {
    private final HouseStateService houseStateService;

    @GetMapping("/average")
    public String averagedMeasures(Model model) {
        List<HouseStateDto> hourlyList = houseStateService.aggregateOnInterval(60, null, null, 3);
        List<HouseStateDto> minutelyList = houseStateService.aggregateOnInterval(5, null, 1, null);
        model.addAttribute("hstates", hourlyList);
        model.addAttribute("mstates", minutelyList);
        return "average";
    }

}
