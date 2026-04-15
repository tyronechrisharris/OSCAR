package com.botts.impl.sensor.rs350;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.vast.data.TextEncodingImpl;

public class ConnectionStatusOutput extends AbstractSensorOutput<RS350Sensor> {

    private static final String SENSOR_OUTPUT_NAME = "connectionStatus";
    private static final String SENSOR_OUTPUT_LABEL = "Connection Status";

    protected DataRecord dataStruct;
    protected DataEncoding dataEncoding;

    private Boolean lastPublishedState;

    public ConnectionStatusOutput(RS350Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
    }

    public void init() {
        RADHelper radHelper = new RADHelper();

        DataComponent samplingTime = radHelper.createPrecisionTimeStamp();
        DataComponent isConnected = radHelper.createBoolean()
                .name("isConnected")
                .label("Is Connected")
                .definition(RADHelper.getRadUri("ConnectionStatus"))
                .description("Whether the sensor is actively receiving messages")
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

    public void publishStatus(boolean connected) {
        if (lastPublishedState != null && lastPublishedState.booleanValue() == connected) {
            return;
        }

        DataBlock dataBlock;

        if (latestRecord == null) {
            dataBlock = dataStruct.createDataBlock();
        } else {
            dataBlock = latestRecord.renew();
        }

        long timeStamp = System.currentTimeMillis() / 1000;

        dataBlock.setLongValue(0, timeStamp);
        dataBlock.setBooleanValue(1, connected);

        lastPublishedState = connected;
        eventHandler.publish(new DataEvent(timeStamp, ConnectionStatusOutput.this, dataBlock));
    }

    @Override
    public DataComponent getRecordDescription() {
        return dataStruct;
    }

    @Override
    public DataEncoding getRecommendedEncoding() {
        return dataEncoding;
    }

    @Override
    public double getAverageSamplingPeriod() {
        return 0;
    }
}
