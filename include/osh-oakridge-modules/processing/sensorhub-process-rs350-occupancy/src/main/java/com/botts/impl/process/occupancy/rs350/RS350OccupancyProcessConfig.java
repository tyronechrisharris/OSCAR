package com.botts.impl.process.occupancy.rs350;

import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.processing.ProcessConfig;

public class RS350OccupancyProcessConfig extends ProcessConfig {

    @DisplayInfo.Required
    @DisplayInfo(desc = "Serial number or unique identifier")
    public String serialNumber = "rs350-process001";

    @DisplayInfo.FieldType(DisplayInfo.FieldType.Type.SYSTEM_UID)
    @DisplayInfo(label = "Parent System (Containing RS350)", desc = "Parent system to read data from. If blank, uses parent system's UID.")
    public String systemUID;

}
