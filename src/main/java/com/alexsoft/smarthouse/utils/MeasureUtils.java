package com.alexsoft.smarthouse.utils;

import com.alexsoft.smarthouse.db.entity.Measure;

public class MeasureUtils {
    public static boolean measureIsNotNull(Measure measure) {
        return measure != null && !measure.isNull();
    }

}
