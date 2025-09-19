import { Stack, Typography } from "@mui/material";
import React from "react";

import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/types";
import { getProcessUnsavedNewName, getScenario, isProcessRenamed } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import { ScenarioVersion } from "../../ScenarioVersion";
import { getProcessingModeVariantName } from "./getProcessingModeVariantName";
import { PanelScenarioDetailsIcon, ProcessName, ProcessRename } from "./ScenarioDetailsComponents";

export function ScenarioNameItem() {
    const scenario = useAppSelector(getScenario);
    const isRenamePending = useAppSelector(isProcessRenamed);
    const unsavedNewName = useAppSelector(getProcessUnsavedNewName);
    const ProcessingModeIcon =
        scenario.processingMode === ProcessingMode.streaming
            ? StreamingIcon
            : scenario.processingMode === ProcessingMode.batch
            ? BatchIcon
            : RequestResponseIcon;

    return (
        <>
            <PanelScenarioDetailsIcon title={getProcessingModeVariantName(scenario.processingMode)}>
                <ProcessingModeIcon />
            </PanelScenarioDetailsIcon>
            <Stack direction="row" sx={{ overflow: "hidden", alignItems: "baseline" }} spacing={0.5}>
                {isRenamePending ? (
                    <ProcessRename variant={"subtitle2"} title={scenario.name}>
                        {unsavedNewName}*
                    </ProcessRename>
                ) : (
                    <ProcessName variant={"subtitle2"} title={scenario.name}>
                        {scenario.name}
                    </ProcessName>
                )}
                <Typography variant="overline">
                    <ScenarioVersion />
                </Typography>
            </Stack>
        </>
    );
}
