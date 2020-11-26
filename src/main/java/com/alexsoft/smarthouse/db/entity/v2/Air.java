package com.alexsoft.smarthouse.db.entity.v2;

import javax.persistence.CascadeType;
import javax.persistence.Embeddable;
import javax.persistence.OneToOne;

import lombok.Data;

@Data
@Embeddable
public class Air {

    @OneToOne(cascade = CascadeType.ALL)
    private Temp temp;

    @OneToOne(cascade = CascadeType.ALL)
    private Quality quality;

    @OneToOne(cascade = CascadeType.ALL)
    private Pressure pressure;

    @OneToOne(cascade = CascadeType.ALL)
    private Wind wind;
}
