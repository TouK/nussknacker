import { Box, Chip, Link, Typography } from "@mui/material";
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
import { useWindows, WindowKind } from "../../../windowManager";
import { CssFade } from "../../CssFade";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
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

const ScenarioDetails = memo((props: ToolbarPanelProps) => {
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
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioDetails.title", "Scenario details")}>
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
                        <Box display={"flex"} mt={1} alignItems={"center"}>
                            <Typography variant={"caption"}>{i18next.t("panels.scenarioDetails.category", "Category:")}</Typography>
                            <Chip size={"small"} label={scenario.processCategory} sx={{ ml: 1 }} />
                        </Box>
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
