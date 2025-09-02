import { Box, FormLabel } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { NumericInput } from "../../graph/node-modal/editors/expression/NumericInput";
import { InfoTooltip } from "../../graph/node-modal/editors/InfoTooltip";
import { StyledLoadingButton } from "../../graph/node-modal/node-action-buttons/StyledLoadingButton";

interface Props {
    handleGenerateTestData: (numberOfSamples: number) => void;
}

export const AppendRowButton = ({ handleGenerateTestData }: Props) => {
    const { t } = useTranslation();
    const [recordsToAppend, setRecordsToAppend] = useState(10);
    return (
        <Box display={"flex"} justifyContent={"flex-start"}>
            <Box display={"flex"} justifyContent={"center"} alignItems={"center"} gap={0.5}>
                <StyledLoadingButton
                    sx={{ ml: 1, mr: 2, fontSize: "14px" }}
                    title={t("testingDialog.appendRecordsButton", "Append from live data")}
                    action={() => handleGenerateTestData(recordsToAppend)}
                />
            </Box>
            <NumericInput
                onChange={(_, value: number) => {
                    const min = 1;
                    const max = 20;
                    const clamped = Math.max(min, Math.min(max, value));
                    setRecordsToAppend(clamped);
                }}
                value={recordsToAppend}
            />
            <FormLabel sx={{ display: "flex", alignItems: "center", ml: 1 }}>{t("testingDialog.labels.records", "Records")}</FormLabel>
            <Box display={"flex"} alignItems={"center"} ml={1}>
                <InfoTooltip title={"The table will be appended with live data from the data sources."} variant={"hover"} />
            </Box>
        </Box>
    );
};
