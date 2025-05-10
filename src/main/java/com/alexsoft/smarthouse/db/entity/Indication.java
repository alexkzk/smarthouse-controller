package com.alexsoft.smarthouse.db.entity;

import static com.alexsoft.smarthouse.utils.Constants.ISO_DATE_TIME_PATTERN;

import com.alexsoft.smarthouse.enums.AggregationPeriod;
import com.alexsoft.smarthouse.enums.InOut;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(schema = "main")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Indication implements Comparable<Indication> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "indication_sq")
    @SequenceGenerator(schema = "main", sequenceName = "indication_sq",
        name = "indication_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonProperty("messageIssued")
    private LocalDateTime issued;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonProperty("messageReceivedUtc")
    private LocalDateTime receivedUtc;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonProperty("messageReceivedLocal")
    private LocalDateTime receivedLocal;

    private String publisherId;

    @JsonProperty("measurePlace")
    private String indicationPlace;

    @Enumerated(EnumType.STRING)
    private InOut inOut;

    @Enumerated(EnumType.STRING)
    private AggregationPeriod aggregationPeriod;

    @OneToOne(cascade = CascadeType.ALL)
    private Air air;

    @Transient
    private String metar;

    @Override
    public int compareTo(Indication o) {
        if (receivedUtc == null) {
            return -1;
        } else if (o == null || o.getReceivedUtc() == null) {
            return  -1;
        } else {
            return o.getReceivedUtc().compareTo(receivedUtc);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Indication that = (Indication) o;
        return Objects.equals(id, that.id) && Objects.equals(issued, that.issued) && Objects.equals(
                receivedUtc, that.receivedUtc) && Objects.equals(publisherId, that.publisherId) && Objects.equals(indicationPlace, that.indicationPlace) && inOut == that.inOut && aggregationPeriod == that.aggregationPeriod && Objects.equals(air, that.air);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, issued, receivedUtc, publisherId, indicationPlace, inOut, aggregationPeriod, air);
    }

    @Override
    public String toString() {
        return "\n{" +
                "publisherId='" + publisherId + '\'' +
                ", indicationPlace='" + indicationPlace + '\'' +
                ", inOut=" + inOut +
                ", aggregationPeriod=" + aggregationPeriod +
                ", " + air +
                '}';
    }
}
