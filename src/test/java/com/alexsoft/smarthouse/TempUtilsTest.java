package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.utils.TempUtils;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TempUtilsTest {

    private final TempUtils tempUtils = new TempUtils();

    //todo to unite next for tests to a parametrized one
    @Test
    public void calculateRelativeHumidityTest() {
        assertThat(tempUtils.calculateRelativeHumidity(18.17, 14.0), is(77));
    }

    @Test
    public void calculateRelativeHumidityDewpointZeroReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(14.2, 0.0), is(38));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(14.2, -1.0), is(35));
    }

    @Test
    public void calculateRelativeHumidityNegativeTempAndDewpointReturnsCorrectHumidTest() {
        assertThat(tempUtils.calculateRelativeHumidity(-5.0, -10.0), is(68));
    }

    @Test
    public void calculateRelativeHumidityDewpointNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(14.2, null));
    }

    @Test
    public void calculateRelativeHumidityTempNullReturnsNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(14.2, null));
    }

    @Test
    public void calculateRelativeHumidityDewpointGreaterThanTemp_shouldReturnNullTest() {
        assertNull(tempUtils.calculateRelativeHumidity(-5.0, -3.0));
    }

    @Test
    public void calculateAbsoluteHumidityTest() {
        assertThat(tempUtils.calculateAbsoluteHumidity(25.5, 45), is(10.7));
    }

    @Test
    public void calculateAbsoluteHumidityNullTemp_shouldReturnNullTest() {
        assertNull(tempUtils.calculateAbsoluteHumidity(null, 45));
    }

    @Test
    public void calculateAbsoluteHumidityNullRh_shouldReturnNullTest() {
        assertNull(tempUtils.calculateAbsoluteHumidity(23.0, null));
    }

}
