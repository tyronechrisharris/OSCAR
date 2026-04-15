package com.botts.impl.sensor.rs350;

import com.botts.impl.sensor.rs350.messages.RS350Message;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.vast.data.TextEncodingImpl;

public class ConnectionStatusOutput extends OutputBase {

    private static final String SENSOR_OUTPUT_NAME = "RS350 Connection Status";
    private static final String SENSOR_OUTPUT_LABEL = "Connection Status";

    public ConnectionStatusOutput(RS350Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
    }

    @Override
    public void init() {
        RADHelper radHelper = new RADHelper();

        var samplingTime = radHelper.createPrecisionTimeStamp();
        var isConnected = radHelper.createBoolean()
                .name("isConnected")
                .label("Is Connected")
                .definition(RADHelper.getRadUri("ConnectionStatus"))
                .description("Is sensor receiving messages")
                .build();

        dataStruct = radHelper.createRecord()
                .name(getName())
                .label(SENSOR_OUTPUT_LABEL)
                .updatable(true)
                .addField(samplingTime.getName(), samplingTime)
                .addField(isConnected.getName(), isConnected)
                .build();

        dataEncoding = new TextEncodingImpl(",", "\n");
    }

    public void publishStatus(boolean isConnected) {
        createOrRenewDataBlock();

        long timeStamp = System.currentTimeMillis();

        dataBlock.setDoubleValue(0, timeStamp / 1000.0);
        dataBlock.setBooleanValue(1, isConnected);

        eventHandler.publish(new DataEvent(timeStamp, ConnectionStatusOutput.this, dataBlock));
    }

    @Override
    public void onNewMessage(RS350Message message) {
        // Not used for connection status, which is updated by the sensor/handler state
    }

    @Override
    public boolean acceptsMessage(RS350Message msg) {
        return false;
    }
}
