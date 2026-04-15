/***************************** BEGIN LICENSE BLOCK ***************************
 Copyright (C) 2023 Botts Innovative Research, Inc. All Rights Reserved.
 ******************************* END LICENSE BLOCK ***************************/
package com.botts.impl.sensor.rs350;

import org.sensorhub.api.comm.CommProviderConfig;
import org.sensorhub.api.config.DisplayInfo;
import org.sensorhub.api.sensor.SensorConfig;

public class RS350Config extends SensorConfig {

    @DisplayInfo.Required
    public String serialNumber;

    @DisplayInfo(desc = "Communication settings to connect to RS-350 data stream")
    public CommProviderConfig<?> commSettings;

    @DisplayInfo.Required
    @DisplayInfo(label = "Outputs", desc = "Configuration options for source data outputs from driver")
    public RS350Outputs outputs = new RS350Outputs();

    @DisplayInfo(label = "Enable WebID Integration", desc = "Enables integration with WebID for isotope analysis")
    public boolean enableWebId = false;

    @DisplayInfo(label = "Enable WebID Bucket Archival", desc = "Enables archival of WebID analysis results to the bucket service")
    public boolean enableWebIdArchival = false;

    @DisplayInfo(label = "Enable Connection Status", desc = "Enables the connection status output to monitor driver connectivity")
    public boolean enableConnectionStatus = true;
}