package com.alexsoft.smarthouse.service;

import com.alexsoft.smarthouse.entity.IndicationV3;
import com.alexsoft.smarthouse.repository.IndicationRepositoryV3;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndicationServiceV3 {

    private final IndicationRepositoryV3 indicationRepositoryV3;

    public IndicationV3 save(IndicationV3 indication) {
        return indicationRepositoryV3.save(indication);
    }

    public void saveAll(Iterable<IndicationV3> indications) {
        indicationRepositoryV3.saveAll(indications);
    }


}
