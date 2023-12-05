import React, { memo } from "react";
import i18next from "i18next";
import { SwitchTransition } from "react-transition-group";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getFetchedProcessDetails, getProcessUnsavedNewName, isProcessRenamed } from "../../../reducers/selectors/graph";
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
    const process = useSelector((state: RootState) => getFetchedProcessDetails(state));
    const isRenamePending = useSelector((state: RootState) => isProcessRenamed(state));
    const unsavedNewName = useSelector((state: RootState) => getProcessUnsavedNewName(state));
    const processState = useSelector((state: RootState) => getProcessState(state));
    const customActions = useSelector((state: RootState) => getCustomActions(state));

    const description = ProcessStateUtils.getStateDescription(process, processState);
    const transitionKey = ProcessStateUtils.getTransitionKey(process, processState);
    // TODO: better styling of process info toolbar in case of many custom actions

    return (
        <ToolbarWrapper title={i18next.t("panels.status.title", "Status")} id={id}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelProcessInfo>
                        <PanelProcessInfoIcon>
                            <ProcessStateIcon process={process} processState={processState} />
                        </PanelProcessInfoIcon>
                        <ProcessInfoText>
                            {isRenamePending ? (
                                <ProcessRename title={process.name}>{unsavedNewName}*</ProcessRename>
                            ) : (
                                <ProcessName>{process.name}</ProcessName>
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
