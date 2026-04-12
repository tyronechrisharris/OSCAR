package com.botts.impl.process.occupancy.rs350;

import org.sensorhub.impl.utils.rad.RADHelper;
import net.opengis.swe.v20.DataRecord;
import net.opengis.swe.v20.DataType;

public class DailyFileStruct {
    public static DataRecord create() {
        RADHelper fac = new RADHelper();
        return fac.createRecord()
                .name("dailyFile")
                .label("Daily File")
                .definition(RADHelper.getRadUri("DailyFile"))
                .addField("samplingTime", fac.createPrecisionTimeStamp())
                .addField("isAlarming", fac.createBoolean()
                        .name("isAlarming")
                        .label("Is Alarming")
                        .description("Used to manage the FFmpeg recording")
                        .build())
                .build();
    }
}
