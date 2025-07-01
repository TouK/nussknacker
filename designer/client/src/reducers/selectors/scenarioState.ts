import { createSelector } from "reselect";

import type { ProcessStateType } from "../../components/Process/types";
import { isStatusRunning, KnownStatusName } from "../../components/Process/types";
import type { RootState } from "../index";

export const getProcessState = (state: RootState): ProcessStateType | null => state.scenarioState;

export const getIsRunning = createSelector(getProcessState, (state) => {
    return isStatusRunning(state?.status);
});

export const getIsRunningOrScheduled = createSelector(getProcessState, (state) => {
    return [KnownStatusName.Running, KnownStatusName.Scheduled].includes(state?.status?.name);
});

export const getIsDeploying = createSelector(getProcessState, (state) => {
    return [KnownStatusName.Deploying].includes(state?.status?.name);
});

export const getIsRedeploying = createSelector(getProcessState, (state) => {
    return [KnownStatusName.Deploying].includes(state?.status?.name);
});
