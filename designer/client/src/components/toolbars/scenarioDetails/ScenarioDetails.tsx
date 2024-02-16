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
import {
    PanelScenarioDetails,
    PanelScenarioDetailsIcon,
    ScenarioDetailsItemWrapper,
    ProcessName,
    ProcessRename,
} from "./ScenarioDetailsComponents";
import { Chip, Link, Typography } from "@mui/material";
import BatchIcon from "../../../assets/img/batch.svg";
import RequestResponseIcon from "../../../assets/img/request-response.svg";
import StreamingIcon from "../../../assets/img/streaming.svg";
import { ProcessingMode } from "../../../http/HttpService";
import { useWindows, WindowKind } from "../../../windowManager";

const ScenarioDetails = memo(({ id }: ToolbarPanelProps) => {
    const { open } = useWindows();

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
        <ToolbarWrapper title={i18next.t("panels.scenarioDetails.title", "Scenario details")} id={id}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelScenarioDetails>
                        <ScenarioDetailsItemWrapper>
                            <PanelScenarioDetailsIcon>
                                <ProcessingModeIcon />
                            </PanelScenarioDetailsIcon>
                            {isRenamePending ? (
                                <ProcessRename title={scenario.name}>{unsavedNewName}*</ProcessRename>
                            ) : (
                                <ProcessName variant={"subtitle2"}>{scenario.name}</ProcessName>
                            )}
                        </ScenarioDetailsItemWrapper>
                        <ScenarioDetailsItemWrapper>
                            <PanelScenarioDetailsIcon>
                                <ProcessStateIcon scenario={scenario} processState={processState} />
                            </PanelScenarioDetailsIcon>
                            <Typography variant={"caption"}>{description}</Typography>
                        </ScenarioDetailsItemWrapper>
                        <div>
                            <Chip size={"small"} label={scenario.processCategory} sx={{ mt: 1 }} />
                        </div>
                        <div>
                            <Typography
                                component={Link}
                                variant={"overline"}
                                color={"text"}
                                sx={(theme) => ({ cursor: "pointer", textDecorationColor: theme.palette.text.secondary })}
                                onClick={() =>
                                    open({
                                        kind: WindowKind.scenarioDetails,
                                        meta: { scenario, processState },
                                    })
                                }
                            >
                                {i18next.t("panels.scenarioDetails.moreButton", "More scenario details")}
                            </Typography>
                        </div>
                    </PanelScenarioDetails>
                </CssFade>
            </SwitchTransition>
        </ToolbarWrapper>
    );
});

ScenarioDetails.displayName = "ScenarioDetails";

export default ScenarioDetails;
