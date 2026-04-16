"use client"


import {Box, Grid} from "@mui/material";
import ConSysApi from "osh-js/source/core/datasource/consysapi/ConSysApi.datasource";
import React, {useCallback, useEffect, useRef, useState} from "react";
import CurveLayer from "osh-js/source/core/ui/layer/CurveLayer";
import ChartJsView from "osh-js/source/core/ui/view/chart/ChartJsView";
import {EventType} from "osh-js/source/core/event/EventType";
import {
    createGammaViewCurve,
    createNeutronViewCurve,
} from "@/app/utils/ChartUtils";

export class ChartInterceptProps {
    laneName: string;
    datasources: {
        gamma: typeof ConSysApi,
        neutron: typeof ConSysApi,
        threshold: typeof ConSysApi
    };
    setChartReady: Function;
}

export default function ChartLane({laneName, datasources, setChartReady}: ChartInterceptProps){

    const gammaChartID = "chart-view-gamma";
    const neutronChartID = "chart-view-neutron";

    const [gammaCurve, setGammaCurve] = useState<typeof CurveLayer>();
    const [neutronCurve, setNeutronCurve] = useState<typeof CurveLayer>();

    const gammaChartViewRef = useRef<typeof ChartJsView | null>(null);
    const neutronChartViewRef = useRef<typeof ChartJsView | null>(null);

    const patchChartSorting = (chartView: any) => {
        const originalUpdate = chartView.chart.update.bind(chartView.chart);
        chartView.chart.update = (mode?: any) => {
            for (const dataset of chartView.chart.data.datasets) {
                if (dataset.data?.length > 1) {
                    dataset.data.sort((a: any, b: any) => a.x - b.x);
                }
            }
            originalUpdate(mode);
        };
    };

    useEffect(() => {
        let rafId: number | null = null;

        const scheduleUpdate = () => {
            if (rafId != null) return;
            rafId = requestAnimationFrame(() => {
                rafId = null;
                if (gammaChartViewRef.current && gammaChartViewRef.current.chart) {
                    gammaChartViewRef.current.chart.update("none");
                }
                if (neutronChartViewRef.current && neutronChartViewRef.current.chart) {
                    neutronChartViewRef.current.chart.update("none");
                }
            });
        };

        if (datasources.gamma) datasources.gamma.subscribe(scheduleUpdate, [EventType.DATA]);
        if (datasources.neutron) datasources.neutron.subscribe(scheduleUpdate, [EventType.DATA]);

        return () => {
            if (rafId != null) cancelAnimationFrame(rafId);

            if (datasources.gamma && typeof (datasources.gamma as any).unsubscribe === 'function') {
                (datasources.gamma as any).unsubscribe(scheduleUpdate, [EventType.DATA]);
            }
            if (datasources.neutron && typeof (datasources.neutron as any).unsubscribe === 'function') {
                (datasources.neutron as any).unsubscribe(scheduleUpdate, [EventType.DATA]);
            }
        };
    }, [datasources.gamma, datasources.neutron]);

    useEffect(() => {
        if(datasources.gamma)
            setGammaCurve(createGammaViewCurve(datasources.gamma));

        if(datasources.neutron)
            setNeutronCurve(createNeutronViewCurve(datasources.neutron));

    }, [datasources.gamma, datasources.neutron]);

    const checkForMountableAndCreateCharts = useCallback(() => {
        if (gammaCurve && !gammaChartViewRef.current) {
            const container = document.getElementById(gammaChartID);

            if (container) {
                gammaChartViewRef.current = new ChartJsView({
                    type: 'line',
                    container: gammaChartID,
                    layers: [gammaCurve],
                    css: "chart-view",
                    options:{
                        plugins: {
                            title: {
                                display: true,
                                text: 'Gamma Chart',
                                font: {
                                    size: 14,
                                    weight: 'bold'
                                },
                                align: 'center',
                                position: 'top',

                            },
                            legend: {
                                display: true,
                                align: 'center',
                                position: 'bottom',
                            }
                        },
                        responsive: true,
                        scales: {
                            x: {
                                title: {
                                    display: true,
                                    text: 'Time',
                                },
                            },
                            y:{
                                title:{
                                    display: true,
                                    text: 'CPS',

                                },
                                display: true,
                                position: 'left',
                                align: 'center',
                                grid: {beginAtZero: false},
                                ticks: {
                                },


                            },
                        },
                    },
                });
                patchChartSorting(gammaChartViewRef.current);
            }
        }

        if (neutronCurve && !neutronChartViewRef.current) {
            const containerN = document.getElementById(neutronChartID);

            if (containerN) {
                neutronChartViewRef.current = new ChartJsView({
                    container: neutronChartID,
                    layers: [neutronCurve],
                    css: "chart-view",
                    options: {
                        plugins: {
                            title: {
                                display: true,
                                text: 'Neutron Chart',
                                font: {
                                    size: 14,
                                    weight: 'bold'
                                },
                                align: 'center',
                                position: 'top',
                                padding: {
                                    top: 10,
                                    bottom: 10,
                                }
                            },
                            legend: {
                                display: true,
                                align: 'right',
                                position: 'bottom',
                            }
                        },
                        responsive: true,
                        scales: {
                            x: {
                                title: {
                                    display: true,
                                    text: 'Time',
                                },
                            },
                            y: {
                                title: {
                                    display: true,
                                    text: 'CPS',
                                },
                                display: true,
                                position: 'left',
                                align: 'center',
                                ticks: {
                                    stepSize: 1
                                },

                            },
                        }
                    },
                });
                patchChartSorting(neutronChartViewRef.current);
            }
        }

        if (gammaCurve || neutronCurve) {
            setChartReady(true);
        }

    }, [gammaCurve, neutronCurve, setChartReady]);

    useEffect(() => {
        checkForMountableAndCreateCharts();
    }, [checkForMountableAndCreateCharts]);


    return (
        <Box display='flex' alignItems="center">
            <Grid container direction="row" marginTop={2} marginLeft={1} spacing={4}>
                <Grid item xs>
                    <div id={gammaChartID} style={{marginBottom: 50, height: '85%',}}></div>
                </Grid>
                <Grid item xs>
                    <div id={neutronChartID} style={{marginBottom: 50, height: '85%',}}></div>
                </Grid>
            </Grid>
        </Box>
    );
};
