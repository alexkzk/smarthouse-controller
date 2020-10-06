
package com.alexsoft.smarthouse.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "meta",
    "altimeter",
    "clouds",
    "flight_rules",
    "other",
    "sanitized",
    "visibility",
    "wind_direction",
    "wind_gust",
    "wind_speed",
    "wx_codes",
    "raw",
    "station",
    "time",
    "remarks",
    "dewpoint",
    "remarks_info",
    "runway_visibility",
    "temperature",
    "wind_variable_direction",
    "units"
})
public class Metar {

    @JsonProperty("meta")
    public Meta meta;
    @JsonProperty("altimeter")
    public Altimeter altimeter;
    @JsonProperty("clouds")
    public List<Cloud> clouds = null;
    @JsonProperty("flight_rules")
    public String flightRules;
    @JsonProperty("other")
    public List<Object> other = null;
    @JsonProperty("sanitized")
    public String sanitized;
    @JsonProperty("visibility")
    public Visibility visibility;
    @JsonProperty("wind_direction")
    public WindDirection windDirection;
    @JsonProperty("wind_gust")
    public WindGust windGust;
    @JsonProperty("wind_speed")
    public WindSpeed windSpeed;
    @JsonProperty("wx_codes")
    public List<WxCode> wxCodes = null;
    @JsonProperty("raw")
    public String raw;
    @JsonProperty("station")
    public String station;
    @JsonProperty("time")
    public Time time;
    @JsonProperty("remarks")
    public String remarks;
    @JsonProperty("dewpoint")
    public Dewpoint dewpoint;
    @JsonProperty("remarks_info")
    public RemarksInfo remarksInfo;
    @JsonProperty("runway_visibility")
    public List<Object> runwayVisibility = null;
    @JsonProperty("temperature")
    public Temperature temperature;
    @JsonProperty("wind_variable_direction")
    public List<Object> windVariableDirection = null;
    @JsonProperty("units")
    public Units units;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
