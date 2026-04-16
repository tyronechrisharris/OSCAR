package com.botts.impl.sensor.rapiscan.output;

import com.botts.impl.sensor.rapiscan.RapiscanSensor;
import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import net.opengis.swe.v20.DataEncoding;
import net.opengis.swe.v20.DataRecord;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.api.sensor.PositionConfig.LLALocation;
import org.sensorhub.impl.sensor.AbstractSensorOutput;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.vast.data.TextEncodingImpl;

import java.time.Instant;


public class LocationOutput  extends AbstractSensorOutput<RapiscanSensor> {
    private static final String SENSOR_OUTPUT_NAME = "location";
    private static final String SENSOR_OUTPUT_LABEL = "Location";

    protected DataRecord dataStruct;
    protected DataEncoding dataEncoding;
    protected DataBlock dataBlock;

    public LocationOutput(RapiscanSensor rapiscanSensor){
        super(SENSOR_OUTPUT_NAME, rapiscanSensor);
    }

    public void init() {
        RADHelper radHelper = new RADHelper();

        var samplingTime = radHelper.createPrecisionTimeStamp();

        dataStruct = radHelper.createRecord()
                .name(getName())
                .label(SENSOR_OUTPUT_LABEL)
                .definition(RADHelper.getRadUri("location-output"))
                .addField(samplingTime.getName(), samplingTime)
                .addField("sensorLocation", radHelper.createLocationVectorLLA()
                        .label("Sensor Location"))
                .build();

        dataEncoding = new TextEncodingImpl(",", "\n");

    }

    public void setLocationOutput(LLALocation gpsLocation) {
        if(gpsLocation == null) {
            return;
        }

        if (latestRecord == null) {
            dataBlock = dataStruct.createDataBlock();
        } else {
            dataBlock = latestRecord.renew();
        }

        long timeStamp = System.currentTimeMillis();
        latestRecordTime = timeStamp;

        int index = 0;
        dataBlock.setTimeStamp(index++, Instant.ofEpochMilli(timeStamp));
        dataBlock.setDoubleValue(index++, gpsLocation.lat);
        dataBlock.setDoubleValue(index++, gpsLocation.lon);
        dataBlock.setDoubleValue(index, gpsLocation.alt);

        latestRecord = dataBlock;
        eventHandler.publish(new DataEvent(timeStamp, LocationOutput.this, dataBlock));
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
