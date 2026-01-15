import { Box } from "@mui/material";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { getInputDataRecords } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { NumericInput } from "../../graph/node-modal/editors/expression/NumericInput";
import { FormLabel } from "../../graph/node-modal/editors/FormControl";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip/InfoTooltip";
import { StyledLoadingButton } from "../../graph/node-modal/node-action-buttons/StyledLoadingButton";

interface Props {
    handleGenerateTestData: (numberOfSamples: number) => void;
    maxTestingRecords: number;
    recordsToAddLimitExceeded: boolean;
}

const DEFAULT_APPEND_COUNT = 10;
const APPEND_MIN = 0;
const TOOLTIP_APPEND_LIVE_DATA = "The table will be appended with live data from the data sources.";

export const AppendFromLiveDataButton = ({ handleGenerateTestData, maxTestingRecords, recordsToAddLimitExceeded }: Props) => {
    const { t } = useTranslation();
    const [recordsToAppend, setRecordsToAppend] = useState<number>(DEFAULT_APPEND_COUNT);
    const allTestingDataRecords = useAppSelector(getInputDataRecords);
    const currentRecordsNumber = allTestingDataRecords.length;
    const maxLiveDataToAppend = maxTestingRecords - currentRecordsNumber;

    useEffect(() => {
        if (maxLiveDataToAppend <= DEFAULT_APPEND_COUNT) {
            setRecordsToAppend(maxLiveDataToAppend > 0 ? maxLiveDataToAppend : APPEND_MIN);
        }
    }, [maxLiveDataToAppend]);

    return (
        <Box display={"flex"} justifyContent={"flex-start"}>
            <Box display={"flex"} justifyContent={"center"} alignItems={"center"} gap={0.5}>
                <StyledLoadingButton
                    sx={{ mr: 2, fontSize: "14px" }}
                    title={t("testingDialog.appendRecordsButton", "Append from live data")}
                    action={() => handleGenerateTestData(recordsToAppend ?? APPEND_MIN)}
                    disabled={recordsToAddLimitExceeded}
                />
            </Box>
            <NumericInput
                onChange={(_, value: unknown) => {
                    const num = typeof value === "number" ? value : Number(value);
                    if (!Number.isFinite(num)) return; // ignore non-numeric input

                    const clamped = Math.max(APPEND_MIN, Math.min(maxLiveDataToAppend, num));
                    setRecordsToAppend(clamped);
                }}
                value={recordsToAppend}
                data-testid={"numberOfRecords"}
            />
            <FormLabel sx={{ display: "flex", alignItems: "center", ml: 1 }}>{t("testingDialog.labels.records", "Records")}</FormLabel>
            <Box display={"flex"} alignItems={"center"} ml={1}>
                <InfoTooltip title={TOOLTIP_APPEND_LIVE_DATA} variant={"hover"} />
            </Box>
        </Box>
    );
};
