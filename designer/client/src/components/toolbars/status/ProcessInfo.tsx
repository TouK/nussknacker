import React, { memo } from "react";
import i18next from "i18next";
import { SwitchTransition } from "react-transition-group";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getProcessUnsavedNewName, getScenario, isProcessRenamed } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { CssFade } from "../../CssFade";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import { PanelProcessInfo, PanelProcessInfoIcon, ProcessInfoItemWrapper, ProcessName, ProcessRename } from "./ProcessInfoComponents";
import { Typography } from "@mui/material";
import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/HttpService";

const ProcessInfo = memo(({ id }: ToolbarPanelProps) => {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const isRenamePending = useSelector((state: RootState) => isProcessRenamed(state));
    const unsavedNewName = useSelector((state: RootState) => getProcessUnsavedNewName(state));
    const processState = useSelector((state: RootState) => getProcessState(state));

    const description = ProcessStateUtils.getStateDescription(scenario, processState);
    const transitionKey = ProcessStateUtils.getTransitionKey(scenario, processState);

    const ProcessingModeIcon =
        scenario.processingMode === ProcessingMode.streaming
            ? StreamingIcon
            : scenario.processingMode === ProcessingMode.batch
            ? BatchIcon
            : RequestResponseIcon;

    return (
        <ToolbarWrapper title={i18next.t("panels.status.title", "Scenario details")} id={id}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelProcessInfo>
                        <ProcessInfoItemWrapper>
                            <PanelProcessInfoIcon>
                                <ProcessingModeIcon />
                            </PanelProcessInfoIcon>
                            {isRenamePending ? (
                                <ProcessRename title={scenario.name}>{unsavedNewName}*</ProcessRename>
                            ) : (
                                <ProcessName variant={"subtitle2"}>{scenario.name}</ProcessName>
                            )}
                        </ProcessInfoItemWrapper>
                        <ProcessInfoItemWrapper>
                            <PanelProcessInfoIcon>
                                <ProcessStateIcon scenario={scenario} processState={processState} />
                            </PanelProcessInfoIcon>
                            <Typography variant={"caption"}>{description}</Typography>
                        </ProcessInfoItemWrapper>
                    </PanelProcessInfo>
                </CssFade>
            </SwitchTransition>
        </ToolbarWrapper>
    );
});

ProcessInfo.displayName = "ProcessInfo";

export default ProcessInfo;
