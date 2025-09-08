import { Box, FormLabel } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { NumericInput } from "../../graph/node-modal/editors/expression/NumericInput";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip";
import { StyledLoadingButton } from "../../graph/node-modal/node-action-buttons/StyledLoadingButton";

interface Props {
    handleGenerateTestData: (numberOfSamples: number) => void;
}

const DEFAULT_APPEND_COUNT = 10;
const APPEND_MIN = 1;
const APPEND_MAX = 20;
const TOOLTIP_APPEND_LIVE_DATA = "The table will be appended with live data from the data sources.";

export const AppendRowButton = ({ handleGenerateTestData }: Props) => {
    const { t } = useTranslation();
    const [recordsToAppend, setRecordsToAppend] = useState<number>(DEFAULT_APPEND_COUNT);
    return (
        <Box display={"flex"} justifyContent={"flex-start"} mt={2}>
            <Box display={"flex"} justifyContent={"center"} alignItems={"center"} gap={0.5}>
                <StyledLoadingButton
                    sx={{ mr: 2, fontSize: "14px" }}
                    title={t("testingDialog.appendRecordsButton", "Append from live data")}
                    action={() => handleGenerateTestData(recordsToAppend)}
                />
            </Box>
            <NumericInput
                onChange={(_, value: number) => {
                    const clamped = Math.max(APPEND_MIN, Math.min(APPEND_MAX, value));
                    setRecordsToAppend(clamped);
                }}
                value={recordsToAppend}
            />
            <FormLabel sx={{ display: "flex", alignItems: "center", ml: 1 }}>{t("testingDialog.labels.records", "Records")}</FormLabel>
            <Box display={"flex"} alignItems={"center"} ml={1}>
                <InfoTooltip title={TOOLTIP_APPEND_LIVE_DATA} variant={"hover"} />
            </Box>
        </Box>
    );
};
