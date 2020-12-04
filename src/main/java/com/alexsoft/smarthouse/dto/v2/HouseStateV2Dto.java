package com.alexsoft.smarthouse.dto.v2;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseStateV2Dto {
    private Integer id;
    private LocalDateTime messageIssued;
    private LocalDateTime messageReceived;
    private String publisherId;
    private String measurePlace;
    private AirDto air;

}
