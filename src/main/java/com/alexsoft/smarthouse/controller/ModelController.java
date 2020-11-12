package com.alexsoft.smarthouse.controller;

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import com.alexsoft.smarthouse.db.entity.MeasurePlace;
import com.alexsoft.smarthouse.dto.HouseStateDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@AllArgsConstructor
public class ModelController {
    private final HouseStateService houseStateService;

    @GetMapping("/aqi")
    public String index(Model model) {
        List<HouseStateDto> hourlyList = houseStateService.aggregateOnInterval(60, null, null, 3);
        List<HouseStateDto> minutelyList = houseStateService.aggregateOnInterval(5, null, 1, null);
        model.addAttribute("hstates", hourlyList);
        model.addAttribute("mstates", minutelyList);
        return "hello";
    }

}
