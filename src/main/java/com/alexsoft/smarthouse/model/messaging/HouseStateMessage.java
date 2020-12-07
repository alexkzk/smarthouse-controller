package com.alexsoft.smarthouse.model.messaging;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import static com.alexsoft.smarthouse.utils.Constants.ISO_DATE_TIME_PATTERN;

@Data
public class HouseStateMessage {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime messageIssued;


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = ISO_DATE_TIME_PATTERN)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime messageReceived = ZonedDateTime.now(ZoneId.of("UTC")).toLocalDateTime();

    private String publisherId;

    private String measurePlace;

    private Air air;

}
