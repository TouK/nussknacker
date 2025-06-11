import { Typography } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/HttpService";
import {
    getProcessUnsavedNewName,
    getProcessVersionId,
    getScenario,
    isLatestProcessVersion,
    isProcessRenamed,
} from "../../../reducers/selectors/graph";
import { getProcessingModeVariantName } from "./getProcessingModeVariantName";
import { PanelScenarioDetailsIcon, ProcessName, ProcessRename } from "./ScenarioDetailsComponents";

const ScenarioVersion = () => {
    const currentVersionId = useSelector(getProcessVersionId);
    const isLatestVersion = useSelector(isLatestProcessVersion);
    if (isLatestVersion || !currentVersionId) return null;
    // eslint-disable-next-line i18next/no-literal-string
    return <Typography variant="overline">v{currentVersionId}</Typography>;
};

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
            {isRenamePending ? (
                <ProcessRename variant={"subtitle2"} title={scenario.name}>
                    {unsavedNewName}* <ScenarioVersion />
                </ProcessRename>
            ) : (
                <ProcessName variant={"subtitle2"} title={scenario.name}>
                    {scenario.name} <ScenarioVersion />
                </ProcessName>
            )}
        </>
    );
}
