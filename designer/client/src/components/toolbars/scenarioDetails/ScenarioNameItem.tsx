import { Stack, Typography } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/HttpService";
import { getProcessUnsavedNewName, getScenario, isProcessRenamed } from "../../../reducers/selectors/graph";
import { ScenarioVersion } from "../../ScenarioVersion";
import { getProcessingModeVariantName } from "./getProcessingModeVariantName";
import { PanelScenarioDetailsIcon, ProcessName, ProcessRename } from "./ScenarioDetailsComponents";

export function ScenarioNameItem() {
    const scenario = useSelector(getScenario);
    const isRenamePending = useSelector(isProcessRenamed);
    const unsavedNewName = useSelector(getProcessUnsavedNewName);
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
