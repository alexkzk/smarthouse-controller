package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.appliance.internal.ApplianceController;
import com.alexsoft.smarthouse.core.util.TempUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TempUtilsTest {

    private final TempUtils tempUtils = new TempUtils();

    @Test
    public void calculateRelativeHumidityTest() {
        assertEquals(77, tempUtils.calculateRelativeHumidity(18.17F, 14.0F));
    }

    @Test
    public void calculateRelativeHumidityDewpointZeroReturnsCorrectHumidTest() {
        assertEquals(38, tempUtils.calculateRelativeHumidity(14.2F, 0.0F));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempReturnsCorrectHumidTest() {
        assertEquals(35, tempUtils.calculateRelativeHumidity(14.2F, -1.0F));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempAndDewpointReturnsCorrectHumidTest() {
        assertEquals(68, tempUtils.calculateRelativeHumidity(-5.0F, -10.0F));
    }

    @Test
    public void calculateRelativeHumidityDewpointNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(14.2F, null));
    }

    @Test
    public void calculateRelativeHumidityTempNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(null, 14.0F));
    }

    @Test
    public void calculateRelativeHumidityDewpointGreaterThanTemp_shouldReturnNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(-5.0F, -3.0F));
    }

    @Test
    public void calculateAbsoluteHumidityTest() {
        assertEquals(10.66F, tempUtils.calculateAbsoluteHumidity(25.5F, 45));
    }

    @Test
    public void calculateAbsoluteHumidityNullTemp_shouldReturnNullTest() {
        assertNull(tempUtils.calculateAbsoluteHumidity(null, 45));
    }

    @Test
    public void calculateAbsoluteHumidityNullRh_shouldReturnNullTest() {
        assertNull(tempUtils.calculateAbsoluteHumidity(23.0F, null));
    }

    @Test
    public void increaseDecreaseTempTest() {
        assertEquals(24.5, ApplianceController.increaseTemperature(24.2));
        assertEquals(24.25, ApplianceController.increaseTemperature(24.00001));
        assertEquals(24.75, ApplianceController.increaseTemperature(24.5));
        assertEquals(24.25, ApplianceController.decreaseTemperature(24.5));
        assertEquals(24.0, ApplianceController.decreaseTemperature(24.1));
        assertEquals(24.0, ApplianceController.decreaseTemperature(24.001));
        assertEquals(23.25, ApplianceController.decreaseTemperature(23.31));
    }

}
