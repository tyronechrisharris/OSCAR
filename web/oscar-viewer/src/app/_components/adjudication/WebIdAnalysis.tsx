"use client";

import React, {useCallback, useContext, useEffect, useState} from "react";
import {EventTableData} from "@/lib/data/oscar/TableHelpers";
import {DataSourceContext} from "@/app/contexts/DataSourceContext";
import {DataGrid, GridColDef} from "@mui/x-data-grid";
import { Box, Stack, Typography } from "@mui/material";
import {LaneMapEntry} from "@/lib/data/oscar/LaneCollection";
import DataStream from "osh-js/source/core/consysapi/datastream/DataStream";
import {IWebIdIsotope} from "@/lib/data/oscar/adjudication/WebId";
import WebIdAnalysisResult from "@/lib/data/oscar/adjudication/WebId";
import {WEB_ID_DEF} from "@/lib/data/Constants";
import {EventType} from "osh-js/source/core/event/EventType";
import { useLanguage } from "@/contexts/LanguageContext";


export default function WebIdAnalysis(props: {
    event: EventTableData;
    onLogUpdate?: (log: WebIdAnalysisResult[]) => void;
}) {
    const { t } = useLanguage();
    const laneMapRef = useContext(DataSourceContext).laneMapRef;
    const [webIdLog, setWebIdLog] = useState<any[]>([]);
    const [filteredLog, setFilteredLog] = useState<any[]>([]);

    const locale = navigator.language || 'en-US';

    const logColumns: GridColDef<WebIdAnalysisResult>[] = [
        {
            field: 'time',
            headerName: t('timestamp'),
            minWidth: 140,
            flex: 1,
            type: 'string',
            valueFormatter: (params) => (new Date(params)).toLocaleString(locale, {
                year: 'numeric',
                month: 'numeric',
                day: 'numeric',
                hour: 'numeric',
                minute: 'numeric',
                second: 'numeric'
            }),
        },
        {
            field: 'name',
            headerName: t('name'),
            minWidth: 100,
            flex: 1,
            valueGetter: (value, row) => row.isotopes?.map((i: IWebIdIsotope) => i.name).join(', '),
        },
        {
            field: 'type',
            headerName: t('type'),
            minWidth: 80,
            flex: 0.8,
            valueGetter: (value, row) => row.isotopes?.map((i: IWebIdIsotope) => i.type).join(', '),
        },
        {
            field: 'confidence',
            headerName: t('confidence'),
            minWidth: 90,
            flex: 0.8,
            valueGetter: (value, row) => row.isotopes?.map((i: IWebIdIsotope) => i.confidence).join(', '),
        },
        {
            field: 'confidenceStr',
            headerName: t('confidenceStr'),
            minWidth: 120,
            flex: 1,
            valueGetter: (value, row) => row.isotopes?.map((i: IWebIdIsotope) => i.confidenceStr).join(', '),
        },
        {
            field: 'countRate',
            headerName: t('countRate'),
            minWidth: 90,
            flex: 0.8,
            valueGetter: (value, row) => row.isotopes?.map((i: IWebIdIsotope) => i.countRate).join(', '),
        },
        {
            field: 'isotopeString',
            headerName: t('isotopeString'),
            minWidth: 100,
            flex: 1,
            type: 'string',
        },
        {
            field: 'numIsotopes',
            headerName: t('numIsotopes'),
            minWidth: 80,
            flex: 0.6,
            type: 'number',
        },
        {
            field: 'numAnalysisWarning',
            headerName: t('numWarnings'),
            minWidth: 80,
            flex: 0.6,
            type: 'string',
        },
        {
            field: 'analysisWarning',
            headerName: t('analysisWarning'),
            minWidth: 120,
            flex: 1,
            type: 'string',
        },
        {
            field: 'chiSquare',
            headerName: t('chiSquare'),
            minWidth: 90,
            flex: 0.7,
            type: 'number',
        },
        {
            field: 'detectorResponseFunction',
            headerName: t('drf'),
            minWidth: 80,
            flex: 0.6,
            type: 'string',
        },
        {
            field: 'errorMessage',
            headerName: t('errorMessage'),
            minWidth: 100,
            flex: 1,
            type: 'string',
        },
        {
            field: 'estimatedDose',
            headerName: t('estDose'),
            minWidth: 80,
            flex: 0.6,
            type: 'number',
        }
    ];

    const fetchData = useCallback(async() => {
        const currentLane = props.event.laneId;
        const currLaneEntry: LaneMapEntry = laneMapRef.current.get(currentLane);

        let webIdDatastream: typeof DataStream = currLaneEntry.findDataStreamByObsProperty(WEB_ID_DEF);
        if(!webIdDatastream) {
            console.warn("No WebID Analysis datastream found for this lane");
            return;
        }

        let query = await webIdDatastream.searchObservations(undefined, 100);

        while (query.hasNext()) {
            let obsCollection = await query.nextPage();

            let webIdData = obsCollection.map((obs: any)=> {
                return new WebIdAnalysisResult(obs.resultTime, obs.result);
            })
            setWebIdLog(webIdData)
        }
    },[])

    useEffect(() => {
        if (props.event)
            fetchData();
    }, [props.event]);

    useEffect(() => {
        const currentLane = props.event.laneId;
        const currLaneEntry: LaneMapEntry = laneMapRef.current.get(currentLane);

        let webIdStream = currLaneEntry.findDataStreamByObsProperty(WEB_ID_DEF);

        if(!webIdStream) {
            console.warn("No WebID Analysis datastream found for this lane");
            return;
        }

        const webIdSource = currLaneEntry.datasourcesRealtime?.find((ds: any) => {
            const parts = ds.properties.resource?.split("/");
            return parts && parts[2] === webIdStream.properties.id;
        });

        if (!webIdSource) {
            console.warn("No WebID Analysis data source found for this lane");
            return;
        }

        const handleWebIdObservations = (msg: any) => {
            const results = msg.values?.[0]?.data;

            const webId = new WebIdAnalysisResult("", results)

            setFilteredLog(prev => [webId, ...prev])
        }

        webIdSource.subscribe(handleWebIdObservations, [EventType.DATA]);

        try {
            webIdSource.connect();
        } catch (err) {
            console.error("Error connecting webid source:", err);
        }

        // return () => {
        //     webIdSource.disconnect();
        // }
    }, [props.event]);


    useEffect(() => {
        const filtered = webIdLog.filter((data) => data?.occupancyObsId === props.event.occupancyObsId);
        setFilteredLog(filtered);
    }, [webIdLog, props.event.occupancyObsId]);

    useEffect(() => {
        if (props.onLogUpdate) {
            props.onLogUpdate(filteredLog);
        }
    }, [filteredLog, props.onLogUpdate]);


    return (
        <>
            <Stack spacing={2} sx={{ width: '100%' }}>
                <Stack direction={"column"} spacing={1}>
                    <Typography variant="h5">{t('webIdAnalysisLog')}</Typography>
                </Stack>
                <Box sx={{ height: 400, width: '100%' }}>
                    <DataGrid
                        rows={filteredLog}
                        columns={logColumns}
                        initialState={{
                            pagination: {
                                paginationModel: {
                                    pageSize: 10
                                }
                            }
                        }}
                        pageSizeOptions={[5, 10, 25, 50, 100]}
                        disableRowSelectionOnClick={true}
                    />
                </Box>
            </Stack>
        </>
    );
}
