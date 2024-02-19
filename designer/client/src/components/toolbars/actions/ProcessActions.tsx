import React, { memo } from "react";
import i18next from "i18next";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getCustomActions } from "../../../reducers/selectors/settings";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarButtons } from "../../toolbarComponents/toolbarButtons";
import { ActionButton } from "../../toolbarSettings/buttons";

const ProcessActions = memo(({ id, buttonsVariant, children }: ToolbarPanelProps) => {
    const customActions = useSelector((state: RootState) => getCustomActions(state));

    // TODO: better styling of process info toolbar in case of many custom actions

    return (
        <ToolbarWrapper title={i18next.t("panels.scenarioActions.title", "Scenario actions")} id={id}>
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

ProcessActions.displayName = "customActions";

export default ProcessActions;
