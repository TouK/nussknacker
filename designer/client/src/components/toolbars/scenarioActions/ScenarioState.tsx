import { Typography } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";
import { SwitchTransition } from "react-transition-group";

import type { RootState } from "../../../reducers";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { CssFade } from "../../CssFade";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import ProcessStateUtils from "../../Process/ProcessStateUtils";

export function ScenarioState() {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const processState = useSelector((state: RootState) => getProcessState(state));
    return (
        <>
            <SwitchTransition>
                <CssFade key={ProcessStateUtils.getTransitionKey(scenario, processState)}>
                    <ProcessStateIcon scenario={scenario} processState={processState} />
                </CssFade>
            </SwitchTransition>
            <Typography component={"div"} variant={"body2"}>
                {ProcessStateUtils.getStateDescription(scenario, processState)}
            </Typography>
        </>
    );
}
