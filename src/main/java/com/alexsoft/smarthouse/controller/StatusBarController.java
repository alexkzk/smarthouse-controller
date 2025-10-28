package com.alexsoft.smarthouse.controller;

import com.alexsoft.smarthouse.service.StatusBarService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class StatusBarController {

    private final StatusBarService statusBarService;

    @GetMapping( "/status-bar")
    public String getStatusBarString() {
        return statusBarService.getStatusBarString();
    }

}
