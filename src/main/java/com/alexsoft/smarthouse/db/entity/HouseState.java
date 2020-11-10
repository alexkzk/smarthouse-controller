package com.alexsoft.smarthouse.db.entity;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import one.util.streamex.StreamEx;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.util.CollectionUtils;

import static com.alexsoft.smarthouse.utils.Constants.ISO_DATE_TIME_PATTERN;
import static com.alexsoft.smarthouse.utils.MeasureUtils.measureIsNotNull;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "main")
public class HouseState implements Comparable<HouseState>{

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "house_state_sq")
    @SequenceGenerator(schema = "main", sequenceName = "house_state_sq", name = "house_state_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    /**
     * An MQTT message issue date time
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime messageIssued;

    /**
     * Actual MQTT message receiving date time
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime messageReceived;

    @NonNull
    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    @lombok.Builder.Default
    private List<AirQualityIndication> airQualities = new ArrayList<>();

    @NonNull
    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    @lombok.Builder.Default
    private List<HeatIndication> heatIndications = new ArrayList<>();

    @NonNull
    @OneToMany(mappedBy = "houseState", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    @lombok.Builder.Default
    private List<WindIndication> windIndications = new ArrayList<>();

    public void addIndication(HeatIndication heatIndication) {
        if (measureIsNotNull(heatIndication)) {
            heatIndication.setHouseState(this);
            heatIndications.add(heatIndication);
        }
    }

    public void addIndication(AirQualityIndication airQualityIndication) {
        if (measureIsNotNull(airQualityIndication)) {
            airQualityIndication.setHouseState(this);
            airQualities.add(airQualityIndication);
        }
    }

    public void addIndication(WindIndication windIndication) {
        if (measureIsNotNull(windIndication)) {
            windIndication.setHouseState(this);
            windIndications.add(windIndication);
        }
    }

    public Stream<Measure> getAllMeasures() {
        return StreamEx.of(getAirQualities().stream().map(aqi -> (Measure) aqi))
            .append(getHeatIndications().stream().map(aqi -> (Measure) aqi))
            .append(getWindIndications().stream().map(aqi -> (Measure) aqi));
    }

    public void setParentForAll() {
        getAllMeasures().forEach(measure -> measure.setHouseState(this));
    }

    @JsonIgnore
    public boolean isNull() {
        for (AirQualityIndication airQualityIndication : airQualities) {
            if (!airQualityIndication.isNull()) {
                return false;
            }
        }
        for (HeatIndication heatIndication : heatIndications) {
            if(!heatIndication.isNull()) {
                return false;
            }
        }
        return CollectionUtils.isEmpty(windIndications);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int compareTo(HouseState houseState) {
        return houseState.getMessageReceived().compareTo(getMessageReceived());
    }

    public static class Builder extends HouseStateBuilder {

        @Override public HouseState build() {
            HouseState houseState = super.build();
            houseState.setParentForAll();
            return houseState;
        }
    }

}
