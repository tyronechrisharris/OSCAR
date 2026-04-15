package com.botts.impl.sensor.rs350;

import com.botts.impl.sensor.rs350.messages.RS350Message;
import org.sensorhub.api.data.DataEvent;
import org.sensorhub.impl.utils.rad.RADHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vast.data.DataBlockMixed;
import org.vast.data.TextEncodingImpl;

public class BackgroundOutput extends OutputBase {

    private static final String SENSOR_OUTPUT_NAME = "RS350 Background Report";

    private static final Logger logger = LoggerFactory.getLogger(BackgroundOutput.class);

    public BackgroundOutput(RS350Sensor parentSensor) {
        super(SENSOR_OUTPUT_NAME, parentSensor);
        logger.debug(SENSOR_OUTPUT_NAME + " output created");
    }

    @Override
    protected void init() {
        RADHelper radHelper = new RADHelper();
        final String LIN_SPEC_ID = "lin-spectrum";
        final String CMP_SPEC_ID = "cmp-spectrum";

        dataStruct = radHelper.createRecord()
                .name(getName())
                .label("Background Report")
                .definition(RADHelper.getRadUri("background-report"))
                .addField("SamplingTime", radHelper.createPrecisionTimeStamp())
                .addField("Duration",
                        radHelper.createQuantity()
                                .name("Duration")
                                .label("Duration")
                                .definition(RADHelper.getRadUri("duration")))
                .addField("LinSpectrumSize", radHelper.createArraySize("Lin Spectrum Size", LIN_SPEC_ID))
                .addField("LinSpectrum", radHelper.createLinSpectrum(LIN_SPEC_ID))
                .addField("CmpSpectrumSize", radHelper.createArraySize("Cmp Spectrum Size", CMP_SPEC_ID))
                .addField("CmpSpectrum", radHelper.createCmpSpectrum(CMP_SPEC_ID))
                .addField("GammaGrossCount", radHelper.createGammaGrossCount())
                .addField("Neutron Gross Count", radHelper.createNeutronGrossCount())
                .build();

        dataEncoding = new TextEncodingImpl(",", "\n");
    }

    @Override
    public boolean acceptsMessage(RS350Message message) {
        return message.getRs350BackgroundMeasurement() != null;
    }

    @Override
    public void onNewMessage(RS350Message message) {
        createOrRenewDataBlock();

        latestRecordTime = System.currentTimeMillis() / 1000;

        dataBlock.setLongValue(0, message.getRs350BackgroundMeasurement().getStartDateTime() / 1000);
        dataBlock.setDoubleValue(1, message.getRs350BackgroundMeasurement().getRealTimeDuration());
        dataBlock.setIntValue(2, message.getRs350BackgroundMeasurement().getLinEnCalSpectrum().length);
        ((DataBlockMixed) dataBlock).getUnderlyingObject()[3].setUnderlyingObject(message.getRs350BackgroundMeasurement().getLinEnCalSpectrum());
        dataBlock.setIntValue(4, message.getRs350BackgroundMeasurement().getCmpEnCalSpectrum().length);
        ((DataBlockMixed) dataBlock).getUnderlyingObject()[5].setUnderlyingObject(message.getRs350BackgroundMeasurement().getCmpEnCalSpectrum());
        dataBlock.setDoubleValue(6, message.getRs350BackgroundMeasurement().getGammaGrossCount());
        dataBlock.setDoubleValue(7, message.getRs350BackgroundMeasurement().getNeutronGrossCount());

        eventHandler.publish(new DataEvent(latestRecordTime, BackgroundOutput.this, dataBlock));
    }
}
