package com.alexsoft.smarthouse.controller.presentation;

import com.alexsoft.smarthouse.dto.ChartDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HouseStatePresentationController {

    private final HouseStateService houseStateService;

    @GetMapping("/temp2")
    public String aggregate(Model model) {
        ChartDto chartDto = houseStateService.aggregate();
        model.addAttribute(chartDto);
        return "temp/chart2";
    }
}
