package com.botts.impl.sensor.rs350;

import com.botts.impl.sensor.rs350.messages.RS350Message;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.TextEncodingImpl;

public class AlarmOutput extends OutputBase {

    private static final String SENSOR_OUTPUT_NAME = "RS350 Alarm";

    private static final Logger logger = LoggerFactory.getLogger(AlarmOutput.class);

    public AlarmOutput(RS350Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
        logger.debug(SENSOR_OUTPUT_NAME + " output created");
    }

    @Override
    protected void init() {
        RADHelper radHelper = new RADHelper();

        dataStruct = radHelper.createRecord()
                .name(getName())
                .label("Alarm")
                .definition(RADHelper.getRadUri("Alarm-output"))
                .addField("Sampling Time", radHelper.createPrecisionTimeStamp())
                .addField("Duration",
                        radHelper.createQuantity()
                                .name("Duration")
                                .label("Duration")
                                .definition(RADHelper.getRadUri("duration")))
                .addField("Remark",
                        radHelper.createText()
                                .name("Remark")
                                .label("Remark")
                                .definition(RADHelper.getRadUri("alarm-remark")))
                .addField("MeasurementClassCode", radHelper.createMeasurementClassCode())
                .addField("AlarmCategoryCode", radHelper.createAlarmCatCode())
                .addField("AlarmDescription",
                        radHelper.createText()
                                .name("AlarmDescription")
                                .label("Alarm Description")
                                .definition(RADHelper.getRadUri("alarm-description")))
                .build();

        dataEncoding = new TextEncodingImpl(",", "\n");
    }

    @Override
    public boolean acceptsMessage(RS350Message message) {
        return message.getRs350DerivedData() != null && message.getRs350RadAlarm() != null;
    }

    @Override
    public void onNewMessage(RS350Message message) {
        createOrRenewDataBlock();

        latestRecordTime = System.currentTimeMillis() / 1000;

        dataBlock.setLongValue(0, message.getRs350DerivedData().getStartDateTime() / 1000);
        dataBlock.setDoubleValue(1, message.getRs350DerivedData().getDuration());
        dataBlock.setStringValue(2, safeString(message.getRs350DerivedData().getRemark()));
        dataBlock.setStringValue(3, safeString(message.getRs350DerivedData().getClassCode()));
        dataBlock.setStringValue(4, safeString(message.getRs350RadAlarm().getCategoryCode()));
        dataBlock.setStringValue(5, safeString(message.getRs350RadAlarm().getDescription()));

        eventHandler.publish(new DataEvent(latestRecordTime, AlarmOutput.this, dataBlock));
    }

    private static String safeString(String value) {
        return value != null ? value : "";
    }
}
