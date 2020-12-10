package com.alexsoft.smarthouse.db.entity.v2;

import java.time.LocalDateTime;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import com.alexsoft.smarthouse.db.entity.InOut;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.ToString;

import static com.alexsoft.smarthouse.utils.Constants.ISO_DATE_TIME_PATTERN;

@Data
@Entity
@Table(name = "house_state_v2", schema = "main")
public class HouseStateV2 {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "house_state_v2_sq")
    @SequenceGenerator(schema = "main", sequenceName = "house_state_v2_sq",
        name = "house_state_v2_sq", allocationSize = 1)
    @ToString.Include
    private Integer id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime messageIssued;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime messageReceived;

    private String publisherId;

    private String measurePlace;

    @Enumerated(EnumType.STRING)
    private InOut inOut;

    @OneToOne(cascade = CascadeType.ALL)
    private Air air;

}
