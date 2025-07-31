import React, { memo, type PropsWithChildren } from "react";

import { getScenario } from "../../../reducers/selectors/graph";
import { getLoggedUser } from "../../../reducers/selectors/settings";
import { useAppSelector } from "../../../store/configureStore";
import { ScenarioState } from "../scenarioActions/ScenarioState";
import { PanelScenarioDetails, ScenarioDetailsItemWrapper } from "./ScenarioDetailsComponents";
import { ScenarioLabels } from "./ScenarioLabels";
import { ScenarioNameItem } from "./ScenarioNameItem";

export const ScenarioStatusContent = memo(function ScenarioDetailsContent2({ children }: PropsWithChildren) {
    const loggedUser = useAppSelector(getLoggedUser);
    const { isFragment } = useAppSelector(getScenario);

    return (
        <PanelScenarioDetails sx={{ position: "relative" }}>
            <ScenarioDetailsItemWrapper sx={{ zoom: "1.2", marginRight: 3, overflow: "hidden" }}>
                <ScenarioNameItem />
            </ScenarioDetailsItemWrapper>
            {isFragment ? null : (
                <ScenarioDetailsItemWrapper>
                    <ScenarioState />
                </ScenarioDetailsItemWrapper>
            )}
            <ScenarioLabels readOnly={!loggedUser.isWriter()} />

            {children}
        </PanelScenarioDetails>
    );
});
