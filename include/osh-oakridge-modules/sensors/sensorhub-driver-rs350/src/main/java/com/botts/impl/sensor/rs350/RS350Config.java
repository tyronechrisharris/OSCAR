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

    @DisplayInfo(label = "Enable WebID Integration", desc = "Publishes deferred WebID requests on alarm transitions when WebID services are available")
    public boolean enableWebIdIntegration = false;

    @DisplayInfo(label = "Enable Bucket Archival", desc = "Stores WebID analysis JSON in the bucket service when that service is available")
    public boolean enableWebIdBucketArchival = false;

    @DisplayInfo(label = "WebID Publish Delay (s)", desc = "Delay before publishing a WebID request so occupancy correlation can complete")
    public int webIdPublishDelaySeconds = 12;

    @DisplayInfo(label = "Message Timeout (s)", desc = "Treat the sensor stream as stale when no complete message is received within this interval")
    public int messageTimeoutSeconds = 15;

    @DisplayInfo(label = "Reconnect Interval (s)", desc = "Minimum delay between reconnect attempts once the stream is stale")
    public int reconnectIntervalSeconds = 5;

    @DisplayInfo.Required
    @DisplayInfo(label = "Outputs", desc = "Configuration options for source data outputs from driver")
    public RS350Outputs outputs = new RS350Outputs();
}
