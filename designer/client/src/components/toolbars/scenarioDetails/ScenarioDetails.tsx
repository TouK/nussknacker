import i18next from "i18next";
import React, { memo } from "react";

import type { RootState } from "../../../reducers";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getLoggedUser } from "../../../reducers/selectors/settings";
import { useAppSelector } from "../../../store/configureStore";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { MoreScenarioDetailsButton } from "./buttons/MoreScenarioDetailsButton";
import { CategoryDetails } from "./CategoryDetails";
import { PanelScenarioDetails, ScenarioDetailsItemWrapper } from "./ScenarioDetailsComponents";
import { ScenarioLabels } from "./ScenarioLabels";
import { ScenarioNameItem } from "./ScenarioNameItem";

const ScenarioDetails = memo(function ScenarioDetails(props: ToolbarPanelProps) {
    const scenario = useAppSelector((state: RootState) => getScenario(state));
    const processState = useAppSelector((state: RootState) => getProcessState(state));
    const loggedUser = useAppSelector((state: RootState) => getLoggedUser(state));

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioDetails.title", "Scenario details")}>
            <PanelScenarioDetails>
                <CategoryDetails scenario={scenario} />
                <ScenarioDetailsItemWrapper>
                    <ScenarioNameItem />
                </ScenarioDetailsItemWrapper>
                <ScenarioLabels readOnly={!loggedUser.isWriter()} />
                <MoreScenarioDetailsButton scenario={scenario} processState={processState} />
            </PanelScenarioDetails>
        </ToolbarWrapper>
    );
});

export default ScenarioDetails;
