import { Typography } from "@mui/material";
import React from "react";
import { useSelector } from "react-redux";

import type { RootState } from "../../../reducers";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import ProcessStateUtils from "../../Process/ProcessStateUtils";

export function ScenarioState() {
    const scenario = useSelector((state: RootState) => getScenario(state));
    const processState = useSelector((state: RootState) => getProcessState(state));

    if (scenario.isFragment) return null;

    return (
        <>
            <ProcessStateIcon scenario={scenario} processState={processState} />
            <Typography component={"div"} variant={"body2"}>
                {ProcessStateUtils.getStateDescription(scenario, processState)}
            </Typography>
        </>
    );
}
