package com.alexsoft.smarthouse.controller.presentation;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.alexsoft.smarthouse.dto.v1.HouseStateDto;
import com.alexsoft.smarthouse.service.HouseStateService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@AllArgsConstructor
public class HouseStatePresentationController {

    private static final DateTimeFormatter HOURLY_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("E d, HH:mm");


    private final HouseStateService houseStateService;

    @GetMapping("/average")
    public String averagedMeasures(Model model) {
        List<HouseStateDto> hourlyList = houseStateService.hourlyAggregatedMeasures();
        List<HouseStateDto> minutelyList = houseStateService.minutelyAggregatedMeasures();
        model.addAttribute("hstates", hourlyList);
        model.addAttribute("mstates", minutelyList);
        return "average";
    }

    @GetMapping("/temp")
    public String tempChart(Model model) {
        List<HouseStateDto> hourlyList = houseStateService.hourlyAggregatedMeasures();
        Collections.reverse(hourlyList);

        List<List<Object>> tempData = new ArrayList<>();
        tempData.add(Arrays.asList("Hour", "Outdoor", "Indoor"));    //  todo move adding this header to JavaScript
        hourlyList.forEach(hlist -> {
            tempData.add(Arrays.asList(
                hlist.getMessageReceived().format(HOURLY_DATE_TIME_FORMAT),
                hlist.getOutdoorHeat().getTempCelsius(),
                hlist.getIndoorHeat().getTempCelsius()
            ));
        });

        List<List<Object>> humidData = new ArrayList<>();
        humidData.add(Arrays.asList("Hour", "Out RH", "In RH", "Out AH", "In AH"));    //  todo move adding this header to JavaScript
        hourlyList.forEach(hlist -> {
            humidData.add(Arrays.asList(
                hlist.getMessageReceived().format(HOURLY_DATE_TIME_FORMAT),
                hlist.getOutdoorHeat().getRelativeHumidity(),
                hlist.getIndoorHeat().getRelativeHumidity(),
                hlist.getOutdoorHeat().getAbsoluteHumidity(),
                hlist.getIndoorHeat().getAbsoluteHumidity()
            ));
        });

        model.addAttribute("tempData", tempData);
        model.addAttribute("humidData", humidData);
        return "temp/chart";
    }

}
