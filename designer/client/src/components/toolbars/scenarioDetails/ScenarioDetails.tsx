import { Box, Chip, Typography } from "@mui/material";
import i18next from "i18next";
import React, { memo } from "react";
import { useSelector } from "react-redux";
import { SwitchTransition } from "react-transition-group";
import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/HttpService";
import { RootState } from "../../../reducers";
import { getProcessUnsavedNewName, getScenario, isProcessRenamed } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { CssFade } from "../../CssFade";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import {
    PanelScenarioDetails,
    PanelScenarioDetailsIcon,
    ProcessName,
    ProcessRename,
    ScenarioDetailsItemWrapper,
} from "./ScenarioDetailsComponents";
import { MoreScenarioDetailsButton } from "./buttons/MoreScenarioDetailsButton";

const ScenarioDetails = memo((props: ToolbarPanelProps) => {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const isRenamePending = useSelector((state: RootState) => isProcessRenamed(state));
    const unsavedNewName = useSelector((state: RootState) => getProcessUnsavedNewName(state));
    const processState = useSelector((state: RootState) => getProcessState(state));

    const transitionKey = ProcessStateUtils.getTransitionKey(scenario, processState);

    const ProcessingModeIcon =
        scenario.processingMode === ProcessingMode.streaming
            ? StreamingIcon
            : scenario.processingMode === ProcessingMode.batch
            ? BatchIcon
            : RequestResponseIcon;

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioDetails.title", "Scenario details")}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelScenarioDetails>
                        <Typography variant={"body2"}>{scenario.processCategory} /</Typography>
                        <ScenarioDetailsItemWrapper>
                            <PanelScenarioDetailsIcon>
                                <ProcessingModeIcon />
                            </PanelScenarioDetailsIcon>
                            {isRenamePending ? (
                                <ProcessRename variant={"subtitle2"} title={scenario.name}>
                                    {unsavedNewName}*
                                </ProcessRename>
                            ) : (
                                <ProcessName variant={"subtitle2"}>{scenario.name}</ProcessName>
                            )}
                        </ScenarioDetailsItemWrapper>
                        <MoreScenarioDetailsButton scenario={scenario} processState={processState} />
                    </PanelScenarioDetails>
                </CssFade>
            </SwitchTransition>
        </ToolbarWrapper>
    );
});

ScenarioDetails.displayName = "ScenarioDetails";

export default ScenarioDetails;
