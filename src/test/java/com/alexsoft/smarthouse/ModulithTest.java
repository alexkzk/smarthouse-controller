package com.alexsoft.smarthouse;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules.of(SmartHouseApplication.class).verify();
    }
}
