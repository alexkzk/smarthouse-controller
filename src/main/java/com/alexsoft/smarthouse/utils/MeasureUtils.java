package com.alexsoft.smarthouse.utils;

import com.alexsoft.smarthouse.db.entity.v1.Measure;

public class MeasureUtils {
    public static boolean measureIsNotNull(Measure measure) {
        return measure != null && !measure.isNull();
    }

}
