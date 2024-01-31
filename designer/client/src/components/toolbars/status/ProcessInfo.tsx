import React, { memo } from "react";
import i18next from "i18next";
import { SwitchTransition } from "react-transition-group";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import {
  getScenario,
  getProcessUnsavedNewName,
  isProcessRenamed,
  getProcessVersionId
} from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getCustomActions } from "../../../reducers/selectors/settings";
import { CssFade } from "../../CssFade";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarButtons } from "../../toolbarComponents/toolbarButtons";
import { ActionButton } from "../../toolbarSettings/buttons";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import {
    PanelProcessInfo,
    PanelProcessInfoIcon,
    ProcessInfoDescription,
    ProcessInfoText,
    ProcessName,
    ProcessRename,
} from "./ProcessInfoComponents";

const ProcessInfo = memo(({ id, buttonsVariant, children }: ToolbarPanelProps) => {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const isRenamePending = useSelector((state: RootState) => isProcessRenamed(state));
    const unsavedNewName = useSelector((state: RootState) => getProcessUnsavedNewName(state));
    const processState = useSelector((state: RootState) => getProcessState(state));
    const customActions = useSelector((state: RootState) => getCustomActions(state));
    const versionId = useSelector(getProcessVersionId);

    console.log(versionId);

    const description = ProcessStateUtils.getStateDescription(scenario, processState);
    const transitionKey = ProcessStateUtils.getTransitionKey(scenario, processState);
    // TODO: better styling of process info toolbar in case of many custom actions

    return (
        <ToolbarWrapper title={i18next.t("panels.status.title", "Status")} id={id}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelProcessInfo>
                        <PanelProcessInfoIcon>
                            <ProcessStateIcon scenario={scenario} processState={processState} />
                        </PanelProcessInfoIcon>
                        <ProcessInfoText>
                            {isRenamePending ? (
                                <ProcessRename title={scenario.name}>{unsavedNewName}*</ProcessRename>
                            ) : (
                                <ProcessName>{scenario.name}</ProcessName>
                            )}
                            <ProcessInfoDescription>{description}</ProcessInfoDescription>
                        </ProcessInfoText>
                    </PanelProcessInfo>
                </CssFade>
            </SwitchTransition>
            <ToolbarButtons variant={buttonsVariant}>
                {children}
                {customActions.map((action) => (
                    //TODO: to be replaced by toolbar config
                    <ActionButton name={action.name} key={action.name} />
                ))}
            </ToolbarButtons>
        </ToolbarWrapper>
    );
});

ProcessInfo.displayName = "ProcessInfo";

export default ProcessInfo;
