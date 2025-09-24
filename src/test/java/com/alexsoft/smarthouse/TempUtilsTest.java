package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.controller.ApplianceRestController;
import com.alexsoft.smarthouse.utils.TempUtils;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TempUtilsTest {

    private final TempUtils tempUtils = new TempUtils();

    //todo to unite next four tests to a parametrized one
    @Test
    public void calculateRelativeHumidityTest() {
        assertThat(tempUtils.calculateRelativeHumidity(18.17F, 14.0F), is(77));
    }

    @Test
    public void calculateRelativeHumidityDewpointZeroReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(14.2F, 0.0F), is(38));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(14.2F, -1.0F), is(35));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempAndDewpointReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(-5.0F, -10.0F), is(68));
    }

    @Test
    public void calculateRelativeHumidityDewpointNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(14.2F, null));
    }

    @Test
    public void calculateRelativeHumidityTempNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(14.2F, null));
    }

    @Test
    public void calculateRelativeHumidityDewpointGreaterThanTemp_shouldReturnNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(-5.0F, -3.0F));
    }

    @Test
    public void calculateAbsoluteHumidityTest() {
        assertThat(tempUtils.calculateAbsoluteHumidity(25.5F, 45), is(10.7F));
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
        assertEquals(24.5, ApplianceRestController.increaseTemperature(24.2));
        assertEquals(24.5, ApplianceRestController.increaseTemperature(24.00001));
        assertEquals(25.0, ApplianceRestController.increaseTemperature(24.5));
        assertEquals(24.0, ApplianceRestController.decreaseTemperature(24.5));
        assertEquals(24.0, ApplianceRestController.decreaseTemperature(24.1));
        assertEquals(24.0, ApplianceRestController.decreaseTemperature(24.001));
        assertEquals(23.0, ApplianceRestController.decreaseTemperature(23.31));
    }

}
