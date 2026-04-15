package com.botts.impl.sensor.rs350;

import com.botts.impl.sensor.rs350.messages.RS350Message;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.TextEncodingImpl;

public class LocationOutput extends OutputBase {

    private static final String SENSOR_OUTPUT_NAME = "RS350 Location";

    private static final Logger logger = LoggerFactory.getLogger(LocationOutput.class);

    public LocationOutput(RS350Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
        logger.debug(SENSOR_OUTPUT_NAME + " output created");
    }

    @Override
    protected void init() {
        RADHelper radHelper = new RADHelper();

        dataStruct = radHelper.createRecord()
                .name(getName())
                .label("Location")
                .definition(RADHelper.getRadUri("location-output"))
                .addField("Sampling Time", radHelper.createPrecisionTimeStamp())
                .addField("Sensor Location", radHelper.createLocationVectorLLA())
                .build();

        dataEncoding = new TextEncodingImpl(",", "\n");
    }

    @Override
    public boolean acceptsMessage(RS350Message message) {
        return message.getRs350ForegroundMeasurement() != null
            && message.getRs350ForegroundMeasurement().getLat() != null
            && message.getRs350ForegroundMeasurement().getLon() != null;
    }

    @Override
    public void onNewMessage(RS350Message message) {
        createOrRenewDataBlock();

        latestRecordTime = System.currentTimeMillis() / 1000;

        dataBlock.setLongValue(0, message.getRs350ForegroundMeasurement().getStartDateTime() / 1000);
        dataBlock.setDoubleValue(1, message.getRs350ForegroundMeasurement().getLat());
        dataBlock.setDoubleValue(2, message.getRs350ForegroundMeasurement().getLon());
        dataBlock.setDoubleValue(3, message.getRs350ForegroundMeasurement().getAlt() != null ? message.getRs350ForegroundMeasurement().getAlt() : 0.0);

        eventHandler.publish(new DataEvent(latestRecordTime, LocationOutput.this, dataBlock));
    }
}
