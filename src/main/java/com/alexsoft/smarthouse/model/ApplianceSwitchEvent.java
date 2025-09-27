package com.alexsoft.smarthouse.model;

import com.alexsoft.smarthouse.enums.ApplianceState;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplianceSwitchEvent {
    private String applianceCode;
    private ApplianceState state;
}
