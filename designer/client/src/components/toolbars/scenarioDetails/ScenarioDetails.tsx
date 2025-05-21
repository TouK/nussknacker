import i18next from "i18next";
import React, { memo } from "react";
import { useSelector } from "react-redux";
import { SwitchTransition } from "react-transition-group";

import type { RootState } from "../../../reducers";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { getLoggedUser } from "../../../reducers/selectors/settings";
import { CssFade } from "../../CssFade";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import type { ToolbarPanelProps } from "../../toolbarComponents/ButtonsToolbar";
import { ToolbarWrapper } from "../../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { MoreScenarioDetailsButton } from "./buttons/MoreScenarioDetailsButton";
import { CategoryDetails } from "./CategoryDetails";
import { PanelScenarioDetails, ScenarioDetailsItemWrapper } from "./ScenarioDetailsComponents";
import { ScenarioLabels } from "./ScenarioLabels";
import { ScenarioNameItem } from "./ScenarioNameItem";

const ScenarioDetails = memo(function ScenarioDetails(props: ToolbarPanelProps) {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const processState = useSelector((state: RootState) => getProcessState(state));
    const loggedUser = useSelector((state: RootState) => getLoggedUser(state));

    const transitionKey = ProcessStateUtils.getTransitionKey(scenario, processState);

    return (
        <ToolbarWrapper {...props} title={i18next.t("panels.scenarioDetails.title", "Scenario details")}>
            <SwitchTransition>
                <CssFade key={transitionKey}>
                    <PanelScenarioDetails>
                        <CategoryDetails scenario={scenario} />
                        <ScenarioDetailsItemWrapper>
                            <ScenarioNameItem />
                        </ScenarioDetailsItemWrapper>
                        <ScenarioLabels readOnly={!loggedUser.isWriter()} />
                        <MoreScenarioDetailsButton scenario={scenario} processState={processState} />
                    </PanelScenarioDetails>
                </CssFade>
            </SwitchTransition>
        </ToolbarWrapper>
    );
});

export default ScenarioDetails;
