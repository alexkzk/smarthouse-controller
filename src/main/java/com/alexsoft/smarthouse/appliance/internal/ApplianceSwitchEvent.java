package com.alexsoft.smarthouse.appliance.internal;

import com.alexsoft.smarthouse.appliance.ApplianceState;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplianceSwitchEvent {
    private String applianceCode;
    private ApplianceState state;
}
