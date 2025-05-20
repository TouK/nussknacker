import React, { memo, type PropsWithChildren } from "react";
import { useSelector } from "react-redux";

import { getLoggedUser } from "../../../reducers/selectors/settings";
import { ScenarioState } from "../scenarioActions/ScenarioState";
import { PanelScenarioDetails, ScenarioDetailsItemWrapper } from "./ScenarioDetailsComponents";
import { ScenarioLabels } from "./ScenarioLabels";
import { ScenarioNameItem } from "./ScenarioNameItem";

export const ScenarioStatusContent = memo(function ScenarioDetailsContent2({ children }: PropsWithChildren) {
    const loggedUser = useSelector(getLoggedUser);
    return (
        <PanelScenarioDetails sx={{ position: "relative" }}>
            <ScenarioDetailsItemWrapper sx={{ zoom: "1.2" }}>
                <ScenarioNameItem />
            </ScenarioDetailsItemWrapper>
            <ScenarioDetailsItemWrapper sx={{ zoom: ".8" }}>
                <ScenarioState />
            </ScenarioDetailsItemWrapper>
            <ScenarioLabels readOnly={!loggedUser.isWriter()} />

            {children}
        </PanelScenarioDetails>
    );
});
