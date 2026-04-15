package org.sensorhub.impl.utils.rad.model;

import net.opengis.swe.v20.DataBlock;
import net.opengis.swe.v20.DataComponent;
import org.sensorhub.impl.sensor.SensorSystem;
import org.sensorhub.impl.utils.rad.output.OccupancyOutput;
import org.vast.data.DataArrayImpl;

import java.util.ArrayList;
import java.util.List;

public class OccupancyExtended extends Occupancy {

    private String alarmCategoryCode;

    public String getAlarmCategoryCode() {
        return alarmCategoryCode;
    }

    public void setAlarmCategoryCode(String alarmCategoryCode) {
        this.alarmCategoryCode = alarmCategoryCode;
    }

    public static class Builder extends Occupancy.Builder {
        private String alarmCategoryCode;

        @Override
        public Builder samplingTime(double samplingTime) {
            super.samplingTime(samplingTime);
            return this;
        }

        @Override
        public Builder occupancyCount(int occupancyCount) {
            super.occupancyCount(occupancyCount);
            return this;
        }

        @Override
        public Builder startTime(double startTime) {
            super.startTime(startTime);
            return this;
        }

        @Override
        public Builder endTime(double endTime) {
            super.endTime(endTime);
            return this;
        }

        @Override
        public Builder neutronBackground(double neutronBackground) {
            super.neutronBackground(neutronBackground);
            return this;
        }

        @Override
        public Builder gammaAlarm(boolean hasGammaAlarm) {
            super.gammaAlarm(hasGammaAlarm);
            return this;
        }

        @Override
        public Builder neutronAlarm(boolean hasNeutronAlarm) {
            super.neutronAlarm(hasNeutronAlarm);
            return this;
        }

        @Override
        public Builder maxGammaCount(int maxGammaCount) {
            super.maxGammaCount(maxGammaCount);
            return this;
        }

        @Override
        public Builder maxNeutronCount(int maxNeutronCount) {
            super.maxNeutronCount(maxNeutronCount);
            return this;
        }

        @Override
        public Builder adjudicatedIds(List<String> adjudicatedIds) {
            super.adjudicatedIds(adjudicatedIds);
            return this;
        }

        @Override
        public Builder videoPaths(List<String> videoPaths) {
            super.videoPaths(videoPaths);
            return this;
        }

        @Override
        public Builder webIdObsIds(List<String> webIdObsIds) {
            super.webIdObsIds(webIdObsIds);
            return this;
        }

        public Builder alarmCategoryCode(String alarmCategoryCode) {
            this.alarmCategoryCode = alarmCategoryCode;
            return this;
        }

        public OccupancyExtended build() {
            OccupancyExtended occ = new OccupancyExtended();
            occ.samplingTime = this.samplingTime;
            occ.occupancyCount = this.occupancyCount;
            occ.startTime = this.startTime;
            occ.endTime = this.endTime;
            occ.neutronBackground = this.neutronBackground;
            occ.hasGammaAlarm = this.hasGammaAlarm;
            occ.hasNeutronAlarm = this.hasNeutronAlarm;
            occ.maxGammaCount = this.maxGammaCount;
            occ.maxNeutronCount = this.maxNeutronCount;
            occ.adjudicatedIds = this.adjudicatedIds;
            occ.videoPaths = this.videoPaths;
            occ.webIdObsIds = this.webIdObsIds;
            occ.setAlarmCategoryCode(this.alarmCategoryCode);
            return occ;
        }
    }

    public static DataBlock fromOccupancyExtended(OccupancyExtended occupancy) {
        // We'll need the RADHelper to create the extended record later,
        // but for now let's assume the record passed in has the extra field.
        OccupancyOutput<?> output = new OccupancyOutput<>(new SensorSystem());
        DataComponent resultStructure = output.getRecordDescription();
        DataBlock dataBlock = resultStructure.createDataBlock();
        dataBlock.updateAtomCount();
        resultStructure.setData(dataBlock);

        int index = 0;

        dataBlock.setDoubleValue(index++, occupancy.getSamplingTime());
        dataBlock.setIntValue(index++, occupancy.getOccupancyCount());
        dataBlock.setDoubleValue(index++, occupancy.getStartTime());
        dataBlock.setDoubleValue(index++, occupancy.getEndTime());
        dataBlock.setDoubleValue(index++, occupancy.getNeutronBackground());
        dataBlock.setBooleanValue(index++, occupancy.hasGammaAlarm());
        dataBlock.setBooleanValue(index++, occupancy.hasNeutronAlarm());
        dataBlock.setIntValue(index++, occupancy.getMaxGammaCount());
        dataBlock.setIntValue(index++, occupancy.getMaxNeutronCount());

        int cmdIdsCount = occupancy.getAdjudicatedIds().size();
        dataBlock.setDoubleValue(index++, cmdIdsCount);

        var adjIdsArray = ((DataArrayImpl) resultStructure.getComponent("adjudicatedIds"));
        if (cmdIdsCount > 0) {
            adjIdsArray.updateSize();
            dataBlock.updateAtomCount();
            for (int i = 0; i < occupancy.getAdjudicatedIds().size(); i++) {
                dataBlock.setStringValue(index++, occupancy.getAdjudicatedIds().get(i));
            }
        }

        int filePathsCount = occupancy.getVideoPaths().size();
        dataBlock.setDoubleValue(index++, filePathsCount);

        var filePathsArray = ((DataArrayImpl) resultStructure.getComponent("videoPaths"));
        if (filePathsCount > 0) {
            filePathsArray.updateSize();
            dataBlock.updateAtomCount();
            for (int i = 0; i < occupancy.getVideoPaths().size(); i++) {
                dataBlock.setStringValue(index++, occupancy.getVideoPaths().get(i));
            }
        }

        int webIdObsIdsCount = occupancy.getWebIdObsIds().size();
        dataBlock.setDoubleValue(index++, webIdObsIdsCount);

        var webIdObsIdsArray = ((DataArrayImpl) resultStructure.getComponent("webIdObsIds"));
        if (webIdObsIdsCount > 0) {
            webIdObsIdsArray.updateSize();
            dataBlock.updateAtomCount();
            for (int i = 0; i < occupancy.getWebIdObsIds().size(); i++) {
                dataBlock.setStringValue(index++, occupancy.getWebIdObsIds().get(i));
            }
        }

        // Add the trailing alarmCategoryCode
        if (resultStructure instanceof DataRecord && ((DataRecord)resultStructure).hasField("alarmCategoryCode")) {
             dataBlock.setStringValue(index++, occupancy.getAlarmCategoryCode() != null ? occupancy.getAlarmCategoryCode() : "");
        }

        return dataBlock;
    }

    public static OccupancyExtended toOccupancyExtended(DataBlock dataBlock) {
        int index = 0;

        var samplingTime = dataBlock.getDoubleValue(index++);
        var occupancyCount = dataBlock.getIntValue(index++);
        var startTime = dataBlock.getDoubleValue(index++);
        var endTime = dataBlock.getDoubleValue(index++);
        var neutronBackground = dataBlock.getDoubleValue(index++);
        var gammaAlarm = dataBlock.getBooleanValue(index++);
        var neutronAlarm = dataBlock.getBooleanValue(index++);
        var maxGammaCount = dataBlock.getIntValue(index++);
        var maxNeutronCount = dataBlock.getIntValue(index++);
        var cmdIdsCount = dataBlock.getIntValue(index++);

        List<String> cmdIds = new ArrayList<>();
        for (int i = 0; i < cmdIdsCount; i++)
            cmdIds.add(dataBlock.getStringValue(index++));

        var videoPathCount = dataBlock.getIntValue(index++);

        List<String> videoPaths = new ArrayList<>();
        for (int i = 0; i < videoPathCount; i++)
            videoPaths.add(dataBlock.getStringValue(index++));

        List<String> webIdObsIds = new ArrayList<>();
        var webIdObsIdsCount = dataBlock.getIntValue(index++);
        for (int i = 0; i < webIdObsIdsCount; i++)
            webIdObsIds.add(dataBlock.getStringValue(index++));

        String alarmCategoryCode = "";
        try {
            alarmCategoryCode = dataBlock.getStringValue(index++);
        } catch (Exception ignored) {}

        return new Builder()
                .samplingTime(samplingTime)
                .occupancyCount(occupancyCount)
                .startTime(startTime)
                .endTime(endTime)
                .neutronBackground(neutronBackground)
                .maxGammaCount(maxGammaCount)
                .maxNeutronCount(maxNeutronCount)
                .gammaAlarm(gammaAlarm)
                .neutronAlarm(neutronAlarm)
                .adjudicatedIds(cmdIds)
                .videoPaths(videoPaths)
                .webIdObsIds(webIdObsIds)
                .alarmCategoryCode(alarmCategoryCode)
                .build();
    }
}
