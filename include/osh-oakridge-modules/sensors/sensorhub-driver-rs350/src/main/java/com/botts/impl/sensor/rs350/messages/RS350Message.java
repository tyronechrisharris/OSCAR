package com.botts.impl.sensor.rs350.messages;

import com.botts.impl.utils.n42.AnalysisResultsType;
import com.botts.impl.utils.n42.CharacteristicType;
import com.botts.impl.utils.n42.CharacteristicsType;
import com.botts.impl.utils.n42.DerivedDataType;
import com.botts.impl.utils.n42.EnergyCalibrationType;
import com.botts.impl.utils.n42.RadInstrumentDataType;
import com.botts.impl.utils.n42.RadInstrumentInformationType;
import com.botts.impl.utils.n42.RadMeasurementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

public class RS350Message {

    private static final Logger logger = LoggerFactory.getLogger(RS350Message.class);

    RS350InstrumentInformation rs350InstrumentInformation;
    RS350InstrumentCharacteristics rs350InstrumentCharacteristics;
    RS350Item rs350Item;
    RS350LinEnergyCalibration rs350LinEnergyCalibration;
    RS350CmpEnergyCalibration rs350CmpEnergyCalibration;
    RS350BackgroundMeasurement rs350BackgroundMeasurement;
    RS350ForegroundMeasurement rs350ForegroundMeasurement;
    RS350DerivedData rs350DerivedData;
    RS350RadAlarm rs350RadAlarm;
    boolean radAlarmReceived;

    public RS350Message(RadInstrumentDataType msg) {
        if (msg == null) {
            return;
        }

        buildInstrumentInformation(msg);
        buildInstrumentCharacteristics(msg);
        buildItem(msg);

        radAlarmReceived = false;

        if (msg.getRadMeasurementOrRadMeasurementGroupOrEnergyCalibration() == null) {
            return;
        }

        msg.getRadMeasurementOrRadMeasurementGroupOrEnergyCalibration().forEach(jaxbElement -> {
            Class<?> jaxbType = jaxbElement.getDeclaredType();

            try {
                if (jaxbType == EnergyCalibrationType.class) {
                    parseEnergyCalibration((EnergyCalibrationType) jaxbElement.getValue());
                } else if (jaxbType == RadMeasurementType.class) {
                    parseMeasurement((RadMeasurementType) jaxbElement.getValue());
                } else if (jaxbType == DerivedDataType.class) {
                    parseDerivedData((DerivedDataType) jaxbElement.getValue());
                } else if (jaxbType == AnalysisResultsType.class && !radAlarmReceived) {
                    parseAnalysisResults((AnalysisResultsType) jaxbElement.getValue());
                } else {
                    logger.debug("Unhandled RS350 payload type: {}", jaxbType.getName());
                }
            } catch (Exception e) {
                logger.debug("Skipping malformed RS350 payload section {}", jaxbType.getSimpleName(), e);
            }
        });
    }

    private void buildInstrumentInformation(RadInstrumentDataType msg) {
        RadInstrumentInformationType instrumentInfo = msg.getRadInstrumentInformation();
        if (instrumentInfo == null) {
            return;
        }

        rs350InstrumentInformation = new RS350InstrumentInformation(
            instrumentInfo.getRadInstrumentManufacturerName(),
            instrumentInfo.getRadInstrumentIdentifier(),
            instrumentInfo.getRadInstrumentModelName(),
            instrumentInfo.getRadInstrumentClassCode() != null ? instrumentInfo.getRadInstrumentClassCode().name() : null
        );
    }

    private void buildInstrumentCharacteristics(RadInstrumentDataType msg) {
        try {
            CharacteristicsType instrumentInfoChars = msg.getRadInstrumentInformation().getRadInstrumentCharacteristics().get(0);
            String deviceName = getCharacteristicValue(instrumentInfoChars, 0);
            Double batteryCharge = parseDouble(getCharacteristicValue(instrumentInfoChars, 1));

            if (deviceName != null && batteryCharge != null) {
                rs350InstrumentCharacteristics = new RS350InstrumentCharacteristics(deviceName, batteryCharge);
            }
        } catch (Exception e) {
            logger.debug("RS350 message missing instrument characteristics", e);
        }
    }

    private void buildItem(RadInstrumentDataType msg) {
        try {
            CharacteristicsType itemInfoChars = msg.getRadItemInformation().get(0).getRadItemCharacteristics().get(0);
            String scanMode = getCharacteristicValue(itemInfoChars, 0);
            Double scanNumber = parseDouble(getCharacteristicValue(itemInfoChars, 1));
            Double scanTimeoutNumber = parseDouble(getCharacteristicValue(itemInfoChars, 2));
            String analysisEnabled = getCharacteristicValue(itemInfoChars, 3);

            if (scanMode != null && scanNumber != null && scanTimeoutNumber != null && analysisEnabled != null) {
                rs350Item = new RS350Item(scanMode, scanNumber, scanTimeoutNumber, analysisEnabled);
            }
        } catch (Exception e) {
            logger.debug("RS350 message missing item characteristics", e);
        }
    }

    private void parseEnergyCalibration(EnergyCalibrationType energyCalibrationType) {
        if (energyCalibrationType == null || energyCalibrationType.getId() == null) {
            return;
        }

        switch (energyCalibrationType.getId()) {
            case "LinEnCal":
                rs350LinEnergyCalibration = new RS350LinEnergyCalibration(energyCalibrationType.getCoefficientValues());
                break;

            case "CmpEnCal":
                rs350CmpEnergyCalibration = new RS350CmpEnergyCalibration(energyCalibrationType.getCoefficientValues());
                break;

            default:
                logger.debug("Unknown energy calibration id: {}", energyCalibrationType.getId());
                break;
        }
    }

    private void parseMeasurement(RadMeasurementType radMeasurementType) {
        if (radMeasurementType == null || radMeasurementType.getMeasurementClassCode() == null) {
            return;
        }

        String measurementClassCode = radMeasurementType.getMeasurementClassCode().value();

        switch (measurementClassCode) {
            case "Background":
                rs350BackgroundMeasurement = createBackgroundMeasurement(radMeasurementType);
                break;

            case "Foreground":
                rs350ForegroundMeasurement = createForegroundMeasurement(radMeasurementType);
                break;

            default:
                logger.debug("Unhandled RS350 measurement class code: {}", measurementClassCode);
                break;
        }
    }

    private RS350BackgroundMeasurement createBackgroundMeasurement(RadMeasurementType radMeasurementType) {
        try {
            return new RS350BackgroundMeasurement(
                radMeasurementType.getMeasurementClassCode().name(),
                radMeasurementType.getStartDateTime().toGregorianCalendar().getTimeInMillis(),
                durationToDouble(radMeasurementType.getRealTimeDuration()),
                radMeasurementType.getSpectrum().get(0).getChannelData().getValue(),
                radMeasurementType.getSpectrum().get(1).getChannelData().getValue(),
                radMeasurementType.getGrossCounts().get(0).getCountData().get(0),
                radMeasurementType.getGrossCounts().get(1).getCountData().get(0)
            );
        } catch (Exception e) {
            logger.debug("Could not build RS350 background measurement", e);
            return null;
        }
    }

    private RS350ForegroundMeasurement createForegroundMeasurement(RadMeasurementType radMeasurementType) {
        try {
            Double lat = null;
            Double lon = null;
            Double alt = null;

            try {
                if (radMeasurementType.getRadInstrumentState() != null
                    && radMeasurementType.getRadInstrumentState().getStateVector() != null
                    && radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint() != null
                    && radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getLatitudeValue() != null
                    && radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getLongitudeValue() != null) {

                    lat = radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getLatitudeValue().getValue().doubleValue();
                    lon = radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getLongitudeValue().getValue().doubleValue();

                    if (radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getElevationValue() != null) {
                        alt = radMeasurementType.getRadInstrumentState().getStateVector().getGeographicPoint().getElevationValue().doubleValue();
                    }
                }
            } catch (Exception e) {
                logger.debug("RS350 foreground measurement did not include location data", e);
                lat = null;
                lon = null;
                alt = null;
            }

            return new RS350ForegroundMeasurement(
                radMeasurementType.getMeasurementClassCode().name(),
                radMeasurementType.getStartDateTime().toGregorianCalendar().getTimeInMillis(),
                durationToDouble(radMeasurementType.getRealTimeDuration()),
                radMeasurementType.getSpectrum().get(0).getChannelData().getValue(),
                radMeasurementType.getSpectrum().get(1).getChannelData().getValue(),
                radMeasurementType.getGrossCounts().get(0).getCountData().get(0),
                radMeasurementType.getGrossCounts().get(1).getCountData().get(0),
                radMeasurementType.getDoseRate().get(0).getDoseRateValue().getValue(),
                lat,
                lon,
                alt
            );
        } catch (Exception e) {
            logger.debug("Could not build RS350 foreground measurement", e);
            return null;
        }
    }

    private void parseDerivedData(DerivedDataType derivedDataType) {
        if (derivedDataType == null) {
            return;
        }

        try {
            String remark = null;
            if (derivedDataType.getRemark() != null && !derivedDataType.getRemark().isEmpty()) {
                remark = derivedDataType.getRemark().get(0);
            }

            rs350DerivedData = new RS350DerivedData(
                remark,
                derivedDataType.getMeasurementClassCode() != null ? derivedDataType.getMeasurementClassCode().name() : null,
                derivedDataType.getStartDateTime().toGregorianCalendar().getTimeInMillis(),
                durationToDouble(derivedDataType.getRealTimeDuration())
            );
        } catch (Exception e) {
            logger.debug("Could not build RS350 derived data section", e);
        }
    }

    private void parseAnalysisResults(AnalysisResultsType analysisResultsType) {
        if (analysisResultsType == null || analysisResultsType.getRadAlarm() == null) {
            return;
        }

        analysisResultsType.getRadAlarm().forEach(radAlarmType -> {
            if (radAlarmType != null) {
                rs350RadAlarm = new RS350RadAlarm(
                    radAlarmType.getRadAlarmCategoryCode() != null ? radAlarmType.getRadAlarmCategoryCode().value() : null,
                    radAlarmType.getRadAlarmDescription()
                );
                radAlarmReceived = true;
            }
        });
    }

    private static String getCharacteristicValue(CharacteristicsType characteristicsType, int index) {
        if (characteristicsType == null || characteristicsType.getCharacteristicOrCharacteristicGroup() == null) {
            return null;
        }

        if (index < 0 || index >= characteristicsType.getCharacteristicOrCharacteristicGroup().size()) {
            return null;
        }

        Object characteristic = characteristicsType.getCharacteristicOrCharacteristicGroup().get(index);
        if (!(characteristic instanceof CharacteristicType)) {
            return null;
        }

        return ((CharacteristicType) characteristic).getCharacteristicValue();
    }

    private static Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double durationToDouble(Duration duration) {
        if (duration == null) {
            return 0.0;
        }

        double totalSeconds = 0.0;

        totalSeconds += getDurationField(duration, DatatypeConstants.DAYS) * 86400.0;
        totalSeconds += getDurationField(duration, DatatypeConstants.HOURS) * 3600.0;
        totalSeconds += getDurationField(duration, DatatypeConstants.MINUTES) * 60.0;
        totalSeconds += getDurationField(duration, DatatypeConstants.SECONDS);

        if (duration.getSign() < 0) {
            totalSeconds *= -1.0;
        }

        return totalSeconds;
    }

    private static double getDurationField(Duration duration, DatatypeConstants.Field field) {
        Number value = (Number) duration.getField(field);
        return value != null ? value.doubleValue() : 0.0;
    }

    public RS350InstrumentInformation getRs350InstrumentInformation() {
        return rs350InstrumentInformation;
    }

    public RS350InstrumentCharacteristics getRs350InstrumentCharacteristics() {
        return rs350InstrumentCharacteristics;
    }

    public RS350Item getRs350Item() {
        return rs350Item;
    }

    public RS350LinEnergyCalibration getRs350LinEnergyCalibration() {
        return rs350LinEnergyCalibration;
    }

    public RS350CmpEnergyCalibration getRs350CmpEnergyCalibration() {
        return rs350CmpEnergyCalibration;
    }

    public RS350BackgroundMeasurement getRs350BackgroundMeasurement() {
        return rs350BackgroundMeasurement;
    }

    public RS350ForegroundMeasurement getRs350ForegroundMeasurement() {
        return rs350ForegroundMeasurement;
    }

    public RS350DerivedData getRs350DerivedData() {
        return rs350DerivedData;
    }

    public RS350RadAlarm getRs350RadAlarm() {
        return rs350RadAlarm;
    }
}
