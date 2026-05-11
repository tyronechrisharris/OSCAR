/*
 * Copyright (c) 2024.  Botts Innovative Research, Inc.
 * All Rights Reserved
 */

import DataStream from "osh-js/source/core/consysapi/datastream/DataStream";
import ControlStream from "osh-js/source/core/consysapi/controlstream/ControlStream";
import ConnectedSystemsApi from "osh-js/source/core/consysapi/ConnectedSystemsApi";
import {
    ADJ_DEF,
    ALARM_DEF,
    CONFIG_DEF,
    CONNECTION_DEF,
    DOSE_DEF,
    DURATION_DEF,
    END_DEF,
    GAMMA_COUNT_DEF,
    HLS_VIDEO_DEF,
    LINEARSPEC_DEF,
    LOCATION_VECTOR_DEF,
    NATIONAL_DEF,
    NEUTRON_COUNT_DEF,
    OCCUPANCY_DEF,
    OCCUPANCY_PILLAR_DEF,
    RASTER_IMAGE_DEF,
    REPORT_DEF,
    SENSOR_LOCATION_DEF,
    SITE_DIAGRAM_DEF,
    SPEED_DEF,
    START_DEF,
    TAMPER_STATUS_DEF,
    THRESHOLD_DEF,
    VIDEO_FRAME_DEF,
    WEB_ID_DEF,
} from "@/lib/data/Constants";


export function isLocationDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) =>
        prop.definition?.includes(SENSOR_LOCATION_DEF) || prop.definition?.includes(LOCATION_VECTOR_DEF));
}

export function isVideoDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) =>
        prop.definition?.includes(RASTER_IMAGE_DEF) || prop.definition?.includes(VIDEO_FRAME_DEF));
}

export function isGammaDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    const definitions = datastream.properties.observedProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(ALARM_DEF)) && definitions.some(d => d.includes(GAMMA_COUNT_DEF));
}

export function isNeutronDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    const definitions = datastream.properties.observedProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(ALARM_DEF)) && definitions.some(d => d.includes(NEUTRON_COUNT_DEF));
}

export function isTamperDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(TAMPER_STATUS_DEF));
}

export function isOccupancyDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) =>
        prop.definition?.includes(OCCUPANCY_PILLAR_DEF) || prop.definition?.includes(OCCUPANCY_DEF));
}

export function isConnectionDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(CONNECTION_DEF));
}

export function isSpeedDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(SPEED_DEF));
}

export function isForegroundDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    const definitions = datastream.properties.observedProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(DURATION_DEF))
        && definitions.some(d => d.includes(LINEARSPEC_DEF))
        && definitions.some(d => d.includes(DOSE_DEF));
}

export function isBackgroundDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    const definitions = datastream.properties.observedProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(DURATION_DEF))
        && definitions.some(d => d.includes(LINEARSPEC_DEF))
        && datastream.properties.observedProperties.length < 10;
}

export function isRs350DataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    const definitions = datastream.properties.observedProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(DURATION_DEF))
        && definitions.some(d => d.includes(LINEARSPEC_DEF))
        && definitions.some(d => d.includes(DOSE_DEF));
}

export function isThresholdDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(THRESHOLD_DEF));
}

export function isConfigurationDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(CONFIG_DEF));
}

export function isSiteDiagramPathDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(SITE_DIAGRAM_DEF));
}


export function isReportControlStream(controlStream: typeof ControlStream): boolean {
    if (!hasDefinitionProperties(controlStream))
        return false;

    return controlStream.properties.controlledProperties.some((prop: any) => prop.definition?.includes(REPORT_DEF));
}

export function isNationalControlStream(controlStream: typeof ControlStream): boolean {
    if (!hasDefinitionProperties(controlStream))
        return false;

    const definitions = controlStream.properties.controlledProperties.map((prop: any) => prop.definition || "");
    return definitions.some(d => d.includes(START_DEF))
        && definitions.some(d => d.includes(END_DEF))
        && controlStream.properties.controlledProperties.length === 2;
}

export function isAdjudicationControlStream(controlStream: typeof ControlStream): boolean {
    if (!hasDefinitionProperties(controlStream))
        return false;

    return controlStream.properties.definition?.includes(ADJ_DEF)
        || controlStream.properties.controlledProperties.some((prop: any) => prop.definition?.includes(ADJ_DEF));
}

export function isWebIdAnalysisDataStream(datastream: typeof DataStream): boolean {
    if (!hasDefinitionProperties(datastream))
        return false;

    return datastream.properties.definition?.includes(WEB_ID_DEF)
        || datastream.properties.observedProperties.some((prop: any) => prop.definition?.includes(WEB_ID_DEF));
}

export function isHLSVideoControlStream(controlStream: typeof ControlStream): boolean {
    if (!hasDefinitionProperties(controlStream))
        return false;

    return controlStream.properties.controlledProperties.some((prop: any) => prop.definition?.includes(HLS_VIDEO_DEF));
}


export function hasDefinitionProperties(stream: typeof ConnectedSystemsApi){
    if (!stream || !stream.properties)
        return false;

    if (stream instanceof ControlStream)
        return stream.properties.controlledProperties?.length > 0;
    else if (stream instanceof DataStream)
        return stream.properties.observedProperties?.length > 0;

    return false;
}
