import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";
import { LoadingButton } from "@mui/lab";
import { Box, inputBaseClasses, TextField } from "@mui/material";
import React, { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

interface Props {
    handleGenerateTestData: (numberOfSamples: number) => void;
    maxTestingRecordsToAdd: number;
    recordsToAddLimitExceeded: boolean;
}

const DEFAULT_APPEND_COUNT = 10;
const APPEND_MIN = 1;

export const AppendFromLiveDataButton = ({ handleGenerateTestData, maxTestingRecordsToAdd, recordsToAddLimitExceeded }: Props) => {
    const { t } = useTranslation();
    const [recordsToAppend, setRecordsToAppend] = useState<number>(Math.max(1, Math.min(DEFAULT_APPEND_COUNT, maxTestingRecordsToAdd)));
    const [loading, setLoading] = useState(false);

    const handleClick = useCallback(async () => {
        setLoading(true);
        try {
            await handleGenerateTestData(recordsToAppend ?? APPEND_MIN);
        } finally {
            setLoading(false);
        }
    }, [handleGenerateTestData, recordsToAppend]);

    const handleChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => {
            const num = Number(e.target.value);
            if (!Number.isFinite(num)) return;
            setRecordsToAppend(Math.max(APPEND_MIN, Math.min(maxTestingRecordsToAdd, num)));
        },
        [maxTestingRecordsToAdd],
    );

    useEffect(() => {
        if (maxTestingRecordsToAdd <= DEFAULT_APPEND_COUNT) {
            setRecordsToAppend(maxTestingRecordsToAdd > 0 ? maxTestingRecordsToAdd : APPEND_MIN);
        }
    }, [maxTestingRecordsToAdd]);

    return (
        <Box display="flex" alignItems="center" gap={0.5}>
            <LoadingButton
                variant="text"
                size="small"
                startIcon={<AddCircleOutlineIcon />}
                onClick={handleClick}
                disabled={recordsToAddLimitExceeded}
                loading={loading}
                sx={{ textTransform: "none", fontSize: "14px" }}
            >
                {t("testingDialog.appendRecordsButton", "Append from live data")}
            </LoadingButton>
            <TextField
                type="number"
                value={recordsToAppend}
                onChange={handleChange}
                inputProps={{
                    min: Math.max(1, Math.min(APPEND_MIN, maxTestingRecordsToAdd)),
                    max: maxTestingRecordsToAdd,
                    "data-testid": "numberOfRecords",
                }}
                size="small"
                variant="filled"
                InputProps={{ disableUnderline: true }}
                sx={{
                    width: 52,
                    [`& .${inputBaseClasses.root}`]: { fontSize: "13px", borderRadius: 1 },
                    [`& .${inputBaseClasses.input}`]: { py: 0.375, px: 0.75, textAlign: "center" },
                }}
            />
        </Box>
    );
};
