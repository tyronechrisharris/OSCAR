"use client";

import {FormControl, InputLabel, MenuItem, Select, SelectChangeEvent} from '@mui/material';
import {useEffect, useState} from 'react';
import {useLanguage} from "@/contexts/LanguageContext";

export default function DetectorResponseFunction(props: {
    onSelect: (value: string) => void, // Return selected value
    selectVal: string
}) {

    const { t } = useLanguage();
    const [drfChoices, setDrfChoices] = useState<string[]>([]);

    const handleChange = (event: SelectChangeEvent) => {
        const val = event.target.value;
        props.onSelect(val)
    };

    useEffect(() => {
        fetchDrfValues();
    }, []);

    async function fetchDrfValues(){
        // Try local config first for air-gapped support
        const localConfigUrl = "/config/spectroscopy-info.json";
        const sandiaUrl = "https://full-spectrum.sandia.gov/api/v1/info";

        try {
            const localResponse = await fetch(localConfigUrl);
            if (localResponse.ok) {
                const config = await localResponse.json();
                if (config.allowed_detectors && config.allowed_detectors.length > 0) {
                    setDrfChoices(config.allowed_detectors);
                    console.log('Using local DRF choices from spectroscopy-info.json');
                    return;
                }
            }
        } catch (err) {
            console.warn('Local spectroscopy-info.json not found or invalid, falling back to Sandia API.');
        }

        try {
            const response = await fetch(sandiaUrl, { method: 'GET' });

            if (!response.ok) {
                console.error('Could not reach Sandia spectrum values.');
                return;
            }

            const results = await response.json();
            setDrfChoices(results?.Options[0].possibleValues ?? []);
        } catch (error) {
            console.error('Failed to fetch DRF values from Sandia:', error);
        }
    }

    return (
        <FormControl size="small" fullWidth>
            <InputLabel id="label">{t('detectorResponseFunction')}</InputLabel>
            <Select
                variant="outlined"
                id="label"
                label={t('detectorResponseFunction')}
                value={props.selectVal}
                onChange={handleChange}
                MenuProps={{
                    MenuListProps: {
                        style: {
                            maxHeight: 300
                        }
                    }
                }}
                autoWidth
                style={{minWidth: "12em"}}
            >
                { drfChoices.length > 0 ? (
                    drfChoices.map((item) =>(
                        <MenuItem key={item} value={item}>
                            {item}
                        </MenuItem>
                    ))
                    ) :
                    (
                        <span>No choices</span>
                    )
                }
            </Select>
        </FormControl>
    );
}
