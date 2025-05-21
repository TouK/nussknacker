import i18next from "i18next";
import React, { memo } from "react";

import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarButtons } from "../../toolbarComponents/toolbarButtons";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { PanelScenarioDetails, ScenarioDetailsItemWrapper } from "../scenarioDetails/ScenarioDetailsComponents";
import { ScenarioState } from "./ScenarioState";

const ProcessActions = memo(({ buttonsVariant, children, ...props }: ToolbarPanelProps) => {
    // TODO: better styling of process info toolbar in case of many custom actions
    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioActions.title", "Scenario actions")}>
            <PanelScenarioDetails>
                <ScenarioDetailsItemWrapper>
                    <ScenarioState />
                </ScenarioDetailsItemWrapper>
            </PanelScenarioDetails>
            <ToolbarButtons variant={buttonsVariant}>{children}</ToolbarButtons>
        </ToolbarWrapper>
    );
});

ProcessActions.displayName = "customActions";

export default ProcessActions;
