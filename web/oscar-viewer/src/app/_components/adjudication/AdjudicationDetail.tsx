/*
 * Copyright (c) 2024.  Botts Innovative Research, Inc.
 * All Rights Reserved
 */

"use client";

import {
    SnackbarCloseReason,
    Stack,
    Typography,
    Box,
    Button,
    Paper,
    Snackbar,
    TextField,
    FormControlLabel,
    Checkbox,
    DialogTitle,
    Dialog,
    Grid,
    DialogContent,
    DialogActions,
    FormControl,
    InputLabel,
    MenuItem,
    Select,
    Divider
} from "@mui/material";
import React, {ChangeEvent, useContext, useEffect, useRef, useState} from "react";
import AdjudicationLog from "./AdjudicationLog"
import {EventTableData} from "@/lib/data/oscar/TableHelpers";
import {AdjudicationCode, AdjudicationCodes} from "@/lib/data/oscar/adjudication/models/AdjudicationConstants";
import {DataSourceContext} from "@/app/contexts/DataSourceContext";
import AdjudicationData from "@/lib/data/oscar/adjudication/Adjudication";
import {LaneMapEntry} from "@/lib/data/oscar/LaneCollection";
import ObservationFilter from "osh-js/source/core/consysapi/observation/ObservationFilter";
import ControlStream from "osh-js/source/core/consysapi/controlstream/ControlStream";
import {isAdjudicationControlStream} from "@/lib/data/oscar/Utilities";
import {generateAdjudicationCommandJSON, sendCommand} from "@/lib/data/oscar/OSCARCommands";
import SecondaryInspectionSelect from "./SecondaryInspectionSelect";
import UploadFileRoundedIcon from '@mui/icons-material/UploadFileRounded';
import InsertDriveFileRoundedIcon from '@mui/icons-material/InsertDriveFileRounded';
import AdjudicationSelect from "./AdjudicationSelect";
import IsotopeSelect from "./IsotopeSelect";
import IconButton from "@mui/material/IconButton";
import DeleteOutline from "@mui/icons-material/DeleteOutline"
import {setAdjudicatedEventId, setSelectedEvent} from "@/lib/state/EventDataSlice";
import {useAppDispatch} from "@/lib/state/Hooks";
import {INode} from "@/lib/data/osh/Node";
import {QrCode, CloudUpload} from "@mui/icons-material";
import QrScanner from "qr-scanner";
import {CloseIcon} from "next/dist/client/components/react-dev-overlay/internal/icons/CloseIcon";
import DetectorResponseFunction from "./DetectorResponseFunction";
import SpectrumTypeSelector from "@/app/_components/adjudication/SpectrumTypeSelector";
import WebIdAnalysis from "@/app/_components/adjudication/WebIdAnalysis";
import WebIdAnalysisResult from "@/lib/data/oscar/adjudication/WebId";
import {useSelector} from "react-redux";
import {RootState} from "@/lib/state/Store";
import {selectLaneMap} from "@/lib/state/OSCARLaneSlice";
import {randomUUID} from "osh-js/source/core/utils/Utils";

import { useLanguage } from "@/contexts/LanguageContext";

interface FileWithWebId {
    file: File;
    webIdEnabled: boolean;
    detectorResponseFunction: string;
    spectrumType: string;
    synthesizeBackground: boolean;
    serverPath?: string;
}

interface ScannedDataWithWebId {
    text: string;
    webIdEnabled: boolean;
    detectorResponseFunction: string;
    spectrumType: string;
    synthesizeBackground: boolean;
    serverPath?: string;
}

export default function AdjudicationDetail(props: { event: EventTableData }) {
    const { t } = useLanguage();

    const dispatch = useAppDispatch();

    const [uploadedFiles, setUploadedFiles] = useState<FileWithWebId[]>([])
    const [adjudicationCode, setAdjudicationCode] = useState(AdjudicationCodes.codes[0]);
    const [isotope, setIsotope] = useState<string[]>([]);
    const [secondaryInspection, setSecondaryInspection] = useState('');

    const [vehicleId, setVehicleId] = useState<string>("");
    const [feedback, setFeedback] = useState<string>("");
    const fileInputRef = useRef<HTMLInputElement | null>(null);

    const [openDialog, setOpenDialog] = useState(false);
    const [openConfirmDialog, setOpenConfirmDialog] = useState(false);
    const videoElement = useRef<HTMLVideoElement>(null);
    const [scannedData, setScannedData] = useState<ScannedDataWithWebId[]>([]);
    const scanner = useRef<QrScanner>();
    const [webIdEvidence, setWebIdEvidence] = useState<WebIdAnalysisResult[]>([]);
    const [selectedEvidence, setSelectedEvidence] = useState<string>('');

    const laneMapRef = useContext(DataSourceContext).laneMapRef;

    const adjudication = props.event ? new AdjudicationData(new Date().toISOString(), props.event.occupancyCount, props.event.occupancyObsId) : null;
    const [adjData, setAdjData] = useState<AdjudicationData>(adjudication);

    const [adjSnackMsg, setAdjSnackMsg] = useState('');
    const [colorStatus, setColorStatus] = useState('');
    const [openSnack, setOpenSnack] = useState(false);
    const [shouldFetchLogs, setShouldFetchLogs] = useState<boolean>(false);

    function onFetchComplete() {
        setShouldFetchLogs(false);
    }

    useEffect(() => {
        const loadOccupancyObservation = async () => {
            if (!props.event.occupancyObsId) {
                try {
                    const currentLane = props.event.laneId;
                    const currLaneEntry: LaneMapEntry = laneMapRef.current.get(currentLane);

                    if (!currLaneEntry) {
                        console.error("Lane entry not found:", currentLane);
                        return;
                    }

                    const ds = currLaneEntry.datastreams.find(
                        (ds: any) => ds.properties.id === props.event.dataStreamId
                    );

                    if (!ds) {
                        console.error("Datastream not found:", props.event.dataStreamId);
                        return;
                    }

                    const filter = new ObservationFilter({
                        filter: `startTime='${props.event.startTime}' AND endTime='${props.event.endTime}'`
                    });

                    let query = await ds.searchObservations(filter, 10000);
                    const occupancyObservation = await query.nextPage();

                    if (!occupancyObservation || occupancyObservation.length === 0) {
                        setAdjSnackMsg(t('cannotFindObservation'));
                        setColorStatus('error');
                        setOpenSnack(true);
                        return;
                    }

                    props.event.occupancyObsId = occupancyObservation[0].id;
                    props.event.rpmSystemId = ds.properties["system@id"];

                    setAdjData(prevAdjData => {
                        if (prevAdjData) {
                            const cloned = prevAdjData.clone();
                            cloned.occupancyObsId = occupancyObservation[0].id;
                            return cloned;
                        }
                        return prevAdjData;
                    });

                } catch (err) {
                    console.error(err);
                    setAdjSnackMsg(t('errorLoadingObservation'));
                    setColorStatus('error');
                    setOpenSnack(true);
                }
            }
        };

        loadOccupancyObservation();
    }, [
        props.event.occupancyObsId,
        props.event.laneId,
        props.event.startTime,
        props.event.endTime,
        props.event.dataStreamId,
        laneMapRef,
        t
    ]);

    const handleWebIdAnalysis = (fileIndex: number) => (event: React.ChangeEvent<HTMLInputElement>) => {
        setUploadedFiles(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, webIdEnabled: event.target.checked} : fileData
            )
        );
    };

    const handleQRCodeWebIdAnalysis = (fileIndex: number) => (event: React.ChangeEvent<HTMLInputElement>) => {
        setScannedData(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, webIdEnabled: event.target.checked} : fileData
            )
        );
    };

    const handleCloseQrCodeDialog = () => {
        scanner?.current?.stop();
        scanner.current = undefined;
        setOpenDialog(false);
    }

    const handleQrCode = () => {
        setOpenDialog(true);
    };

    useEffect(() => {
        if (openDialog && !scanner.current) {
            const timeoutId = setTimeout(() => {
                if (!videoElement.current) {
                    console.error("Video element not found");
                    setAdjSnackMsg(t('failedToInitializeCamera'));
                    setOpenSnack(true);
                    return;
                }

                const qrOptions = {
                    onDecodeError: (err: any) => console.error("QR Scan Error:", err),
                    preferredCamera: "environment",
                    highlightScanRegion: true,
                }

                scanner.current = new QrScanner(videoElement.current, (result) => {
                    let newData: ScannedDataWithWebId = {
                        text: result.data,
                        detectorResponseFunction: "",
                        spectrumType: "foreground",
                        webIdEnabled: true,
                        synthesizeBackground: false
                    }

                    setScannedData(prev => {
                        if (prev.some(item => item.text === result.data))
                            return prev;
                        return [...prev, newData]
                    });
                }, qrOptions);

                scanner.current.start().catch((err) => {
                    console.error("Error starting scanner:", err);
                    setAdjSnackMsg(t('failedToStartCamera'));
                    setColorStatus('error');
                    setOpenSnack(true);
                });
            }, 100);

            return () => clearTimeout(timeoutId);
        }
    }, [openDialog, t]);


    const handleFileUpload = (e: ChangeEvent<HTMLInputElement>) => {
        if (e.target.files === null) {
            return;
        }

        const files = Array.from(e.target.files);

        const filesWithWebId = files.map(file => ({
            file,
            webIdEnabled: false,
            detectorResponseFunction: "",
            spectrumType: "foreground",
            synthesizeBackground: false
        }));

        setUploadedFiles([...uploadedFiles, ...filesWithWebId]);
        e.target.value = '';
    };

    const handleFileDelete = (fileIndex: number) => {
        setUploadedFiles((prevState) => prevState.filter((_, i) => i !== fileIndex));
    }

    const handleAdjudicationSelect = (value: AdjudicationCode) => {
        let tAdjData = adjData.clone();
        tAdjData.adjudicationCode = AdjudicationCodes.getCodeObjByLabel(value.label);

        setAdjData(tAdjData);
        setAdjudicationCode(value);
    }

    const handleIsotopeSelect = (value: string[]) => {
        let tAdjData = adjData.clone();
        tAdjData.isotopes = value;
        setIsotope(value);
        setAdjData(tAdjData);
    }

    const handleInspectionSelect = (value: string) => {
        let tAdjData = adjData.clone();
        tAdjData.secondaryInspectionStatus = value;
        setSecondaryInspection(value);
        setAdjData(tAdjData);
    }



    const handleDrfSelection = (fileIndex: number) => (value: string) => {
        setUploadedFiles(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, detectorResponseFunction: value} : fileData
            )
        );
    }

    const handleSpectrumType = (fileIndex: number) => (value: string) => {
        setUploadedFiles(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, spectrumType: value} : fileData
            )
        );
    }

    const handleSynthesizeBackground = (fileIndex: number) => (event: React.ChangeEvent<HTMLInputElement>) => {
        setUploadedFiles(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, synthesizeBackground: event.target.checked} : fileData
            )
        );
    };

    const handleScannedDataDrfSelection = (index: number) => (value: string) => {
        setScannedData(prev =>
            prev.map((data, idx) =>
                idx === index ? {...data, detectorResponseFunction: value} : data
            )
        );
    }

    const handleScannedDataSpectrumType = (index: number) => (value: string) => {
        setScannedData(prev =>
            prev.map((data, idx) =>
                idx === index ? {...data, spectrumType: value} : data
            )
        );
    }

    const handleScannedDataSynthesizeBackground = (fileIndex: number) => (event: React.ChangeEvent<HTMLInputElement>) => {
        setScannedData(prevFiles =>
            prevFiles.map((fileData, idx) =>
                idx === fileIndex ? {...fileData, synthesizeBackground: event.target.checked} : fileData
            )
        );
    };

    const handleScannedDataDelete = (index: number) => {
        setScannedData(prev => prev.filter((_, i) => i !== index));
    }

    const handleWebIdLogUpdate = (log: WebIdAnalysisResult[]) => {
        setWebIdEvidence(log);
    }

    const handleEvidenceSelection = (event: any) => {
        const selectedId = event.target.value;
        setSelectedEvidence(selectedId);

        const evidence = webIdEvidence.find(e => e.id === selectedId);
        if (evidence) {
            let isotopes = evidence.isotopes.map(i => i.name);
            handleIsotopeSelect(isotopes);

            let evidenceNotes = `WebID Evidence Analysis: ${evidence.isotopeString} (Confidence: ${evidence.isotopes[0]?.confidenceStr ?? 'N/A'})`;
            setFeedback(prev => prev ? prev + "\n" + evidenceNotes : evidenceNotes);
            setAdjData(prev => {
                const cloned = prev.clone();
                cloned.feedback = prev.feedback ? prev.feedback + "\n" + evidenceNotes : evidenceNotes;
                return cloned;
            });
        }
    }

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const {name, value} = e.target;

        let tempAdjData = adjData.clone();

        if (name === 'vehicleId') {
            setVehicleId(value);
            tempAdjData.vehicleId = value;
        } else if (name === 'notes') {
            setFeedback(value)
            tempAdjData.feedback = value;
        }

        setAdjData(tempAdjData);
    }

    function resetForm() {
        setVehicleId('')
        setAdjData(adjudication);
        setUploadedFiles([]);
        setScannedData([]);
        setSecondaryInspection('');
        setIsotope([]);
        setAdjudicationCode(AdjudicationCodes.codes[0]);
        setFeedback('')
    }

    const sendAdjudicationData = async () => {
        if (adjData.adjudicationCode === null || !adjData.adjudicationCode || adjData.adjudicationCode === AdjudicationCodes.codes[0]) {
            setAdjSnackMsg(t('selectValidCode'));
            setColorStatus('error');
            setOpenSnack(true)
            return;
        }

        setOpenConfirmDialog(true);
    }

    const confirmAndSubmitAdjudication = async () => {
        setOpenConfirmDialog(false);
        const phenomenonTime = new Date().toISOString();

        const tempAdjData = adjData.clone();

        tempAdjData.setTime(phenomenonTime);
        tempAdjData.setAdjudicationCode(adjData.adjudicationCode);
        tempAdjData.setVehicleId(adjData.vehicleId);
        tempAdjData.setFeedback(adjData.feedback);
        tempAdjData.setIsotopes(adjData.isotopes);
        tempAdjData.setOccupancyObsId(adjData.occupancyObsId);

        const currentLane = props.event.laneId;
        const currLaneEntry: LaneMapEntry = laneMapRef.current.get(currentLane);

        if (!currLaneEntry) {
            setAdjSnackMsg(t('laneNotFound'));
            setColorStatus('error');
            setOpenSnack(true);
            return;
        }

        await submitAdjudication(currLaneEntry, tempAdjData, uploadedFiles);
    }

    const submitAdjudication = async (currLaneEntry: LaneMapEntry, tempAdjData: AdjudicationData, files: FileWithWebId[]) => {
        try {
            if (!currLaneEntry || !currLaneEntry.parentNode) {
                setAdjSnackMsg(t('laneNotFound'));
                setColorStatus('error');
                setOpenSnack(true);
                return;
            }

            const ds = currLaneEntry.datastreams.find((ds: any) => ds.properties.id === props.event.dataStreamId);

            const streams = currLaneEntry.controlStreams && currLaneEntry.controlStreams.length > 0
                ? currLaneEntry.controlStreams
                : await currLaneEntry.parentNode.fetchNodeControlStreams();

            if (!streams) {
                setAdjSnackMsg(t('failedToFetchControlStreams'));
                setColorStatus('error');
                setOpenSnack(true);
                return;
            }

            const adjControlStream = streams.find((stream: typeof ControlStream) => isAdjudicationControlStream(stream));

            if (!adjControlStream) {
                setAdjSnackMsg(t('failedCannotFindAdjudicationControlStream'));
                setColorStatus('error');
                setOpenSnack(true);
                return;
            }

            if (tempAdjData.occupancyObsId === null) {
                let query = await ds.searchObservations(new ObservationFilter({
                    filter: `startTime='${props.event.startTime}' AND endTime='${props.event.endTime}'`
                }), 1);

                const occupancyObservation = await query.nextPage();

                if (!occupancyObservation || occupancyObservation.length === 0) {
                    setAdjSnackMsg(t('cannotFindObservation'));
                    setColorStatus('error')
                    setOpenSnack(true);
                    return;
                }

                props.event.occupancyObsId = occupancyObservation[0].id;
                props.event.rpmSystemId = ds.properties["system@id"];
                tempAdjData.occupancyObsId = occupancyObservation[0].id;
            }

            const alreadyUploaded = files.filter(f => f.serverPath).map(f => f.serverPath as string);
            const alreadyUploadedScanned = scannedData.filter(d => d.serverPath).map(d => d.serverPath as string);

            const needsUpload = [...files.filter(f => !f.serverPath)];
            const unscannedCodes = scannedData.filter(d => !d.serverPath);
            const qrCodeFiles = createFilesForQRCode(unscannedCodes);
            needsUpload.push(...qrCodeFiles);

            const missingDrf = needsUpload.some(f => f.webIdEnabled && !f.detectorResponseFunction);
            if (missingDrf) {
                setAdjSnackMsg(t('selectDrfBeforeUpload'));
                setColorStatus('error');
                setOpenSnack(true);
                return;
            }

            let newFileNames: string[] = [];
            if (needsUpload.length > 0) {
                newFileNames = await sendFileUploadRequest(needsUpload, currLaneEntry.parentNode);
                if (!newFileNames || newFileNames.length === 0) {
                    // Error already handled in sendFileUploadRequest via snackbar
                    return;
                }
            }

            const allFiles = [...alreadyUploaded, ...alreadyUploadedScanned, ...newFileNames];

            const response = await sendCommand(
                currLaneEntry.parentNode,
                adjControlStream.properties.id,
                generateAdjudicationCommandJSON(
                    tempAdjData.feedback,
                    tempAdjData.adjudicationCode,
                    tempAdjData.isotopes,
                    tempAdjData.secondaryInspectionStatus,
                    allFiles,
                    tempAdjData.occupancyObsId,
                    tempAdjData.vehicleId
                )
            );

            if (!response.ok) {
                setAdjSnackMsg(t('adjudicationFail'))
                setColorStatus('error')
                setOpenSnack(true);
                return;
            }

            props.event.adjudicatedData = tempAdjData;

            setAdjSnackMsg(t('adjudicationSuccess') + props.event.occupancyCount);
            setColorStatus('success')

            dispatch(setSelectedEvent(props.event));
            dispatch(setAdjudicatedEventId(props.event.id));

            setShouldFetchLogs(true);
            setOpenSnack(true);
            resetForm();
        } catch (error) {
            console.error(error);
            setAdjSnackMsg(t('adjudicationFail'))
            setColorStatus('error')
            setOpenSnack(true);
        }
    }

    const laneMap = useSelector((state: RootState) => selectLaneMap(state));

    const laneUid = laneMap.get(props.event?.laneId)?.laneSystem?.properties?.properties?.uid;

    function createFilesForQRCode(qrCodeData: ScannedDataWithWebId[]) {
        let newFiles: FileWithWebId[] = [];

        const options = {
            type: "text/plain;charset=utf-8",
        };

        qrCodeData.forEach((code) => {
            let fileName = 'foreground-' + randomUUID() + ".txt"
            let fileBits = code.text;
            const file = new File([fileBits], fileName, options);

            let fileWithId: FileWithWebId = {
                file,
                webIdEnabled: true,
                detectorResponseFunction: code.detectorResponseFunction,
                spectrumType: code.spectrumType ? code.spectrumType : 'foreground',
                synthesizeBackground: code.synthesizeBackground
            }
            newFiles.push(fileWithId);
        })

        return newFiles;
    }

    async function sendFileUploadRequest(filePaths: FileWithWebId[], node: INode): Promise<string[]> {
        const newFileNames: string[] = [];

        if (!node || !node.auth) {
            console.error("Node or auth information is missing");
            return [];
        }

        const encoded = btoa(`${node.auth.username}:${node.auth.password}`);
        const protocol = node.isSecure ? 'https://' : 'http://';

        const webIdFiles = filePaths.filter(f => f.webIdEnabled);
        const foregroundFile = webIdFiles.find(f => f.spectrumType === 'foreground');
        const backgroundFile = webIdFiles.find(f => f.spectrumType === 'background');
        const hasPair = foregroundFile && backgroundFile;

        const pairedFiles = hasPair ? new Set([foregroundFile, backgroundFile]) : new Set<FileWithWebId>();

        if (hasPair) {
            const drf = foregroundFile.detectorResponseFunction || backgroundFile.detectorResponseFunction;
            const endpoint = `${protocol}${node.address}:${node.port}${node.oshPathRoot}${node.bucketsEndpoint}/adjudication?occupancyObsId=${props.event.occupancyObsId}&laneUid=${laneUid}&webIdEnabled=${foregroundFile.webIdEnabled}&drf=${drf}`;
            const url = new URL(endpoint);

            const formData = new FormData();
            formData.append('foreground', foregroundFile.file);
            formData.append('background', backgroundFile.file);

            const options: RequestInit = {
                method: 'POST',
                headers: {'Authorization': `Basic ${encoded}`},
                mode: 'cors',
                body: formData
            };

            try {
                const response = await fetch(url, options);
                if (!response.ok) {
                    console.error("Failed uploading paired WebID files:", response);
                    setAdjSnackMsg(t('failedToUploadWebIdFiles') || 'Failed to upload paired WebID files.');
                    setColorStatus('error');
                    setOpenSnack(true);
                    return [];
                }

                const filePathsResult: string[] = await response.json();
                newFileNames.push(...filePathsResult);
            } catch (err) {
                console.error("Error uploading paired WebID files:", err);
                setAdjSnackMsg(t('errorUploadingFiles'));
                setOpenSnack(true);
                return [];
            }
        }

        for (const fileData of filePaths) {
            if (pairedFiles.has(fileData)) continue;

            let fileName = `adjudication?occupancyObsId=${props.event.occupancyObsId}&laneUid=${laneUid}&webIdEnabled=${fileData.webIdEnabled}`

            if (fileData.webIdEnabled)
                fileName = fileName + `&drf=${fileData.detectorResponseFunction}&spectrumType=${fileData.spectrumType}&synthesizeBackground=${fileData.synthesizeBackground}`



            let endpoint = `${protocol}${node.address}:${node.port}${node.oshPathRoot}${node.bucketsEndpoint}/${fileName}`;

            const url = new URL(endpoint);

            const formData = new FormData();
            formData.append('foreground', fileData.file);

            const options: RequestInit = {
                method: 'POST',
                headers: {'Authorization': `Basic ${encoded}`},
                mode: 'cors',
                body: formData
            };

            try {
                const response = await fetch(url, options);
                if (!response.ok) {
                    console.error("Failed uploading file:", fileData.file.name, response);
                    setAdjSnackMsg(`${t('failedToUploadFile') || 'Failed to upload file'}: ${fileData.file.name}`);
                    setColorStatus('error');
                    setOpenSnack(true);
                    return [];
                }

                const filePathsResult: string[] = await response.json();
                newFileNames.push(...filePathsResult);
            } catch (err) {
                console.error("Error uploading file:", fileData.file.name, err);
                setAdjSnackMsg(`${t('failedToUploadFile') || 'Failed to upload file'}: ${fileData.file.name}`);
                setOpenSnack(true);
                return [];
            }
        }

        return newFileNames;
    }

    const submitToWebId = async () => {
        const needsUpload = uploadedFiles.filter(f => f.webIdEnabled && !f.serverPath);
        const scannedNeedsUpload = scannedData.filter(d => d.webIdEnabled && !d.serverPath);

        if (needsUpload.length === 0 && scannedNeedsUpload.length === 0) {
            setAdjSnackMsg(t('noFilesToUploadToWebId'));
            setOpenSnack(true);
            return;
        }

        const missingDrf = needsUpload.some(f => !f.detectorResponseFunction) ||
                          scannedNeedsUpload.some(d => !d.detectorResponseFunction);

        if (missingDrf) {
            setAdjSnackMsg(t('selectDrfBeforeUpload'));
            setColorStatus('error');
            setOpenSnack(true);
            return;
        }

        const currentLane = props.event.laneId;
        const currLaneEntry: LaneMapEntry = laneMapRef.current.get(currentLane);

        if (!currLaneEntry) {
            setAdjSnackMsg(t('laneNotFound'));
            setColorStatus('error');
            setOpenSnack(true);
            return;
        }

        const allToUpload = [...needsUpload];
        let qrFiles = createFilesForQRCode(scannedNeedsUpload);
        qrFiles.forEach(f => allToUpload.push(f));

        const paths = await sendFileUploadRequest(allToUpload, currLaneEntry.parentNode);

        if (paths.length > 0) {
            // Map paths back to source
            let pathIdx = 0;
            if (needsUpload.length > 0) {
                setUploadedFiles(prev => prev.map(f => {
                    if (f.webIdEnabled && !f.serverPath) return { ...f, serverPath: paths[pathIdx++] };
                    return f;
                }));
            }
            if (scannedNeedsUpload.length > 0) {
                setScannedData(prev => prev.map(d => {
                   if (d.webIdEnabled && !d.serverPath) return { ...d, serverPath: paths[pathIdx++] };
                   return d;
                }));
            }
            setAdjSnackMsg(t('webIdUploadSuccess'));
            setColorStatus('success');
            setOpenSnack(true);
        }
    }

    const handleCloseSnack = (event: React.SyntheticEvent | Event, reason?: SnackbarCloseReason,) => {
        if (reason === 'clickaway')
            return;
        setOpenSnack(false);
    };

    return (
        <Grid container spacing={2} sx={{ width: '100%' }}>
            <Grid item xs={12}>
                <WebIdAnalysis
                    event={props.event}
                    onLogUpdate={handleWebIdLogUpdate}
                />
            </Grid>

            <Grid item xs={12}>
                <Typography
                    variant="h4"
                >
                {t('adjudicationTitle')}
            </Typography>
            </Grid>

            <Grid item xs={12}>
                <AdjudicationLog
                    event={props.event}
                    shouldFetch={shouldFetchLogs}
                    onFetch={onFetchComplete}
                />
            </Grid>

            <Grid item container xs={12} spacing={2}>
                <Grid item xs={12}>
                    <Typography variant="h5">
                        {t('adjudicationReportForm')}
                    </Typography>
                </Grid>
                <Grid item xs={12} sm={3} lg={2}>
                    <TextField
                        label={t('vehicleId')}
                        name="vehicleId"
                        value={vehicleId}
                        onChange={handleChange}
                        fullWidth
                    />
                </Grid>
                <Grid item xs={12} sm={9} lg={10} />
                <Grid item xs={12} sm={6}>
                    <AdjudicationSelect
                        adjCode={adjudicationCode}
                        onSelect={handleAdjudicationSelect}
                    />
                </Grid>
                <Grid item xs={12} sm={6}>
                    <IsotopeSelect
                        isotopeValue={isotope}
                        onSelect={handleIsotopeSelect}
                    />
                </Grid>

                <Grid item xs={12}>
                    <TextField
                        id="outlined-multiline-static"
                        label={t('notes')}
                        name="notes"
                        multiline
                        rows={4}
                        value={feedback}
                        onChange={handleChange}
                        fullWidth
                    />
                </Grid>

                {uploadedFiles.length > 0 && (
                    <Grid item xs={12}>
                        <Paper variant="outlined" sx={{ p: 1 }}>
                            <Stack
                                sx={{
                                    maxHeight: '150px',
                                    overflowY: 'auto',
                                }}
                                spacing={1}
                            >
                                {uploadedFiles.map((fileData, index) => (
                                    <Stack key={`${fileData.file.name}-${index}`} direction="row" spacing={1} alignItems="center" flexWrap="wrap" p={1}>
                                        <Box display="flex" alignItems="center" sx={{ minWidth: 0, flex: '1 1 auto' }}>
                                            <InsertDriveFileRoundedIcon fontSize="small" />
                                            <Typography variant="body2" noWrap sx={{ ml: 0.5 }}>
                                                {fileData.file.name} {fileData.serverPath && `(${t('uploaded')})`}
                                            </Typography>
                                        </Box>

                                        <Stack direction="row" spacing={1} alignItems="center">
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        size="small"
                                                        checked={fileData.webIdEnabled}
                                                        onChange={handleWebIdAnalysis(index)}
                                                        disabled={!!fileData.serverPath}
                                                    />
                                                }
                                                label={<Typography variant="body2">WebID</Typography>}
                                                sx={{mr: 0}}
                                            />

                                                {fileData.webIdEnabled && (

                                                    <>
                                                        <DetectorResponseFunction
                                                            onSelect={handleDrfSelection(index)}
                                                            selectVal={fileData.detectorResponseFunction}
                                                            disabled={!!fileData.serverPath}
                                                        />
                                                        <SpectrumTypeSelector
                                                            onSelect={handleSpectrumType(index)}
                                                            selectVal={fileData.spectrumType}
                                                            disabled={!!fileData.serverPath}
                                                        />
                                                        {
                                                            fileData.spectrumType === 'foreground' && (

                                                                <FormControlLabel
                                                                    control={
                                                                        <Checkbox
                                                                            size="small"
                                                                            checked={fileData.synthesizeBackground}
                                                                            onChange={handleSynthesizeBackground(index)}
                                                                            disabled={!!fileData.serverPath}
                                                                        />
                                                                    }
                                                                    label={<Typography variant="body2">{t('synthesizeBackground')}</Typography>}
                                                                    sx={{mr: 0}}
                                                                />

                                                            )
                                                        }
                                                    </>
                                            )}
                                        </Stack>
                                        <IconButton
                                            onClick={() => handleFileDelete(index)}
                                            size="small"
                                            sx={{
                                                padding: "2px",
                                                border: "1px solid",
                                                borderRadius: "10px",
                                                borderColor: "error.main",
                                                backgroundColor: "inherit",
                                                color: "error.main"
                                            }}>
                                            <DeleteOutline fontSize="small" />
                                        </IconButton>
                                    </Stack>
                                ))}
                            </Stack>
                        </Paper>
                    </Grid>
                )}

                {scannedData.length > 0 && (
                    <Grid item xs={12}>
                        <Paper variant="outlined" sx={{ p: 1 }}>
                            <Stack
                                sx={{
                                    maxHeight: '150px',
                                    overflowY: 'auto',
                                }}
                                spacing={1}
                            >
                                {scannedData.map((data, index) => (
                                    <Stack key={`scanned-${index}`} direction="row" spacing={1} alignItems="center" flexWrap="wrap" p={1}>
                                        <Box display="flex" alignItems="center" sx={{ minWidth: 0, flex: '1 1 auto' }}>
                                            <QrCode fontSize="small" color="action"/>
                                            <Typography variant="body2" noWrap sx={{ ml: 0.5, fontFamily: 'monospace' }}>
                                                {data.text.length > 40 ? data.text.substring(0, 40) + '...' : data.text} {data.serverPath && `(${t('uploaded')})`}
                                            </Typography>
                                        </Box>

                                        <Stack direction="row" spacing={1} alignItems="center">
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        size="small"
                                                        checked={data.webIdEnabled}
                                                        onChange={handleQRCodeWebIdAnalysis(index)}
                                                        disabled={!!data.serverPath}
                                                    />
                                                }
                                                label={<Typography variant="body2">WebID</Typography>}
                                                sx={{mr: 0}}
                                            />

                                            {data.webIdEnabled && (
                                                <>
                                                    <DetectorResponseFunction
                                                        onSelect={handleScannedDataDrfSelection(index)}
                                                        selectVal={data.detectorResponseFunction}
                                                        disabled={!!data.serverPath}
                                                    />
                                                    <SpectrumTypeSelector
                                                        onSelect={handleScannedDataSpectrumType(index)}
                                                        selectVal={data.spectrumType}
                                                        disabled={!!data.serverPath}
                                                    />

                                                    {
                                                        data.spectrumType === 'foreground' && (
                                                            <FormControlLabel
                                                                control={
                                                                    <Checkbox
                                                                        size="small"
                                                                        checked={data.synthesizeBackground}
                                                                        onChange={handleScannedDataSynthesizeBackground(index)}
                                                                        disabled={!!data.serverPath}
                                                                    />
                                                                }
                                                                label={<Typography variant="body2">{t('synthesizeBackground')}</Typography>}
                                                                sx={{mr: 0}}
                                                            />
                                                        )
                                                    }

                                                </>
                                            )}
                                            <IconButton
                                                onClick={() => handleScannedDataDelete(index)}
                                                size="small"
                                                sx={{
                                                    padding: "2px",
                                                    border: "1px solid",
                                                    borderRadius: "10px",
                                                    borderColor: "error.main",
                                                    backgroundColor: "inherit",
                                                    color: "error.main"
                                                }}>
                                                <DeleteOutline fontSize="small" />
                                            </IconButton>
                                        </Stack>
                                    </Stack>
                                ))}
                            </Stack>
                        </Paper>
                    </Grid>
                )}
            </Grid>

            <Grid item xs={12}>
                <Typography variant="h5">
                    {t('evidenceCollection')}
                </Typography>
            </Grid>

            <Grid item container xs={12} spacing={2} alignItems="center">
                {webIdEvidence.length > 0 && (
                    <Grid item xs={12} sm={6}>
                        <FormControl size="small" fullWidth>
                            <InputLabel id="evidence-label">{t('webIdEvidence')}</InputLabel>
                            <Select
                                labelId="evidence-label"
                                value={selectedEvidence}
                                label={t('webIdEvidence')}
                                onChange={handleEvidenceSelection}
                            >
                                {webIdEvidence.map((evidence) => (
                                    <MenuItem key={evidence.id} value={evidence.id}>
                                        {evidence.time} - {evidence.isotopeString}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Grid>
                )}
                <Grid item xs={"auto"}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <Button
                            component="label"
                            startIcon={<UploadFileRoundedIcon/>}
                            sx={{
                                display: "flex",
                                alignItems: "center",
                                width: "auto",
                                padding: "8px",
                                borderStyle: "solid",
                                borderWidth: "1px",
                                borderRadius: "10px",
                                borderColor: "secondary.main",
                                backgroundColor: "inherit",
                                color: "secondary.main"
                            }}
                        >
                            {t('uploadFiles')}
                            <input
                                type="file"
                                multiple
                                onChange={handleFileUpload}
                                ref={fileInputRef}
                                style={{display: "none"}}
                            />
                        </Button>
                        <Button
                            component="label"
                            startIcon={<QrCode/>}
                            sx={{
                                display: "flex",
                                alignItems: "center",
                                width: "auto",
                                padding: "8px",
                                borderStyle: "solid",
                                borderWidth: "1px",
                                borderRadius: "10px",
                                borderColor: "info.main",
                                backgroundColor: "inherit",
                                color: "info.main"
                            }}
                            onClick={handleQrCode}
                        >
                            {t('qrStartScan')}
                        </Button>
                        <Button
                            variant="contained"
                            startIcon={<CloudUpload />}
                            onClick={submitToWebId}
                            sx={{
                                borderRadius: "10px",
                                textTransform: 'none'
                            }}
                        >
                            {t('uploadToWebId')}
                        </Button>
                    </Stack>
                </Grid>
                <Grid item xs={"auto"}>
                    <Stack direction="row" spacing={2} alignItems="center">
                        <SecondaryInspectionSelect
                            secondarySelectVal={secondaryInspection}
                            onSelect={handleInspectionSelect}
                        />
                        <Button
                            disableElevation
                            variant={"contained"}
                            color={"success"}
                            onClick={sendAdjudicationData}
                            sx={{ borderRadius: "10px" }}
                        >
                            {t('submit')}
                        </Button>
                    </Stack>
                </Grid>
            </Grid>

            <Dialog
                onClose={handleCloseQrCodeDialog}
                open={openDialog}
                fullWidth
                maxWidth="sm"
            >
                <IconButton
                    aria-label="close"
                    onClick={handleCloseQrCodeDialog}
                    sx={{
                        position: 'absolute',
                        right: 8,
                        top: 8,
                    }}
                >
                    <CloseIcon/>
                </IconButton>
                <DialogTitle sx={{textAlign: 'center', pb: 1}}>
                    {t('webIdQrAnalysis')}
                </DialogTitle>
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        p: 3,
                        pt: 1,
                    }}
                >
                    <Box className='qr-reader'
                         sx={{
                             width: 400,
                             height: 400,
                             maxWidth: 400,
                             borderRadius: 2,
                             overflow: 'hidden',
                             backgroundColor: 'black',
                             display: 'flex',
                             alignItems: 'center',
                             justifyContent: 'center',
                         }}
                    >
                        <video
                            ref={videoElement}
                            style={{
                                width: "100%",
                                height: "100%",
                                objectFit: "cover"
                            }}
                        />
                    </Box>

                    {scannedData.length > 0 && (
                        <Paper
                            variant="outlined"
                            sx={{mt: 2, p: 2, width: '100%', maxHeight: 150, overflowY: 'auto'}}
                        >
                            <Typography variant="subtitle2" gutterBottom>
                                {t('scannedCodes')} ({scannedData.length}):
                            </Typography>
                            <Stack spacing={1}>
                                {scannedData.map((data, idx) => (
                                    <Stack
                                        key={idx}
                                        direction="row"
                                        justifyContent="space-between"
                                        alignItems="center"
                                    >
                                        <Typography variant="body2" sx={{fontFamily: 'monospace'}}>
                                            {data.text.length > 60 ? data.text.substring(0, 60) + '...' : data.text}
                                        </Typography>
                                        <IconButton
                                            size="small"
                                            onClick={() => handleScannedDataDelete(idx)}
                                        >
                                            <DeleteOutline fontSize="small"/>
                                        </IconButton>
                                    </Stack>
                                ))}
                            </Stack>
                        </Paper>
                    )}

                    <Stack direction="row" spacing={2} sx={{mt: 2}}>
                        {scannedData.length > 0 && (
                            <Button
                                variant="outlined"
                                onClick={() => setScannedData([])}
                            >
                                {t('clearAll')}
                            </Button>
                        )}
                        <Button
                            variant="contained"
                            onClick={handleCloseQrCodeDialog}
                            sx={{minWidth: 120}}
                        >
                            {t('doneScanning') || "Done Scanning"}
                        </Button>
                    </Stack>
                </Box>
            </Dialog>

            <Dialog
                open={openConfirmDialog}
                onClose={() => setOpenConfirmDialog(false)}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>{t('confirmAdjudicationTitle')}</DialogTitle>
                <DialogContent dividers>
                    <Grid container spacing={2}>
                        <Grid item xs={6}>
                            <Typography variant="caption" color="text.secondary">{t('vehicleId')}</Typography>
                            <Typography variant="body1">{vehicleId || t('statusNone')}</Typography>
                        </Grid>
                        <Grid item xs={6}>
                            <Typography variant="caption" color="text.secondary">{t('adjudicationCode')}</Typography>
                            <Typography variant="body1">{adjudicationCode.label}</Typography>
                        </Grid>
                        <Grid item xs={12}>
                            <Typography variant="caption" color="text.secondary">{t('isotopes')}</Typography>
                            <Typography variant="body1">{isotope.join(", ") || t('statusNone')}</Typography>
                        </Grid>
                        <Grid item xs={12}>
                            <Typography variant="caption" color="text.secondary">{t('notes')}</Typography>
                            <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>{feedback || t('statusNone')}</Typography>
                        </Grid>
                        {(uploadedFiles.length > 0 || scannedData.length > 0) && (
                            <Grid item xs={12}>
                                <Typography variant="caption" color="text.secondary">{t('attachedFiles')}</Typography>
                                {uploadedFiles.map((f, i) => (
                                    <Typography key={i} variant="body2">• {f.file.name}</Typography>
                                ))}
                                {scannedData.map((d, i) => (
                                    <Typography key={i} variant="body2">• QR Code ({d.text.substring(0, 20)}...)</Typography>
                                ))}
                            </Grid>
                        )}
                    </Grid>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpenConfirmDialog(false)}>{t('cancel')}</Button>
                    <Button variant="contained" color="success" onClick={confirmAndSubmitAdjudication}>
                        {t('confirmAndSubmit')}
                    </Button>
                </DialogActions>
            </Dialog>

            <Snackbar
                anchorOrigin={{vertical: 'top', horizontal: 'center'}}
                open={openSnack}
                autoHideDuration={5000}
                onClose={handleCloseSnack}
                message={adjSnackMsg}
                sx={{
                    '& .MuiSnackbarContent-root': {
                        backgroundColor: colorStatus === 'success' ? 'green' : 'red',
                    },
                }}
            />
        </Grid>
    );
}