package com.alexsoft.smarthouse.dto;

import java.time.LocalDateTime;
import java.util.List;

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
public class HouseStateDto {

    private Integer id;
    private LocalDateTime issued;
    private LocalDateTime received;
    private List<AqiDto> aqis;
    private List<TempDto> temperatures;

}
