import i18next from "i18next";
import React, { memo } from "react";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getCustomActions } from "../../../reducers/selectors/settings";
import { ToolbarPanelProps } from "../../toolbarComponents/DefaultToolbarPanel";
import { ToolbarButtons } from "../../toolbarComponents/toolbarButtons";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { ActionButton } from "../../toolbarSettings/buttons";

const ProcessActions = memo(({ buttonsVariant, children, ...props }: ToolbarPanelProps) => {
    const customActions = useSelector((state: RootState) => getCustomActions(state));

    // TODO: better styling of process info toolbar in case of many custom actions

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioActions.title", "Scenario actions")}>
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
