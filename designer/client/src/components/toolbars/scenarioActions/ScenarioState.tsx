import { Stack, Typography } from "@mui/material";
import i18next from "i18next";
import React, { useCallback } from "react";
import { SwitchTransition } from "react-transition-group";

import { formatDateTime } from "../../../common/DateUtils";
import { getScenario } from "../../../reducers/selectors/graph";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { useAppSelector } from "../../../store/storeHelpers";
import { CssFade } from "../../CssFade";
import ProcessStateIcon from "../../Process/ProcessStateIcon";
import ProcessStateUtils from "../../Process/ProcessStateUtils";
import type { ProcessStateType, Scenario, StatusRunning } from "../../Process/types";
import { isStatusRunning } from "../../Process/types";
import { RunningVersion } from "../../ScenarioVersion";

export function ScenarioState() {
    const scenario = useAppSelector(getScenario);
    const processState = useAppSelector(getProcessState);
    const runningDescription = useCallback((statusRunning: StatusRunning) => {
        return (
            <Stack>
                <Typography component={"div"} variant={"body2"}>
                    {i18next.t("panels.scenarioStatus.runningVersion", "running version")} <RunningVersion />
                </Typography>
                <Typography component={"div"} variant={"overline"}>
                    {i18next.t("panels.scenarioStatus.runningSince", "since {{startedAt}}", {
                        startedAt: formatDateTime(statusRunning.startedAt),
                    })}
                </Typography>
            </Stack>
        );
    }, []);

    const defaultDescription = useCallback((scenario: Scenario, processState: ProcessStateType) => {
        return (
            <Typography component={"div"} variant={"body2"}>
                {ProcessStateUtils.getStateDescription(scenario, processState)}
            </Typography>
        );
    }, []);

    return (
        <>
            <SwitchTransition>
                <CssFade key={ProcessStateUtils.getTransitionKey(scenario, processState)}>
                    <ProcessStateIcon scenario={scenario} processState={processState} />
                </CssFade>
            </SwitchTransition>
            {isStatusRunning(processState?.status) ? runningDescription(processState.status) : defaultDescription(scenario, processState)}
        </>
    );
}
