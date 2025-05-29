import { createSelector } from "reselect";

import type { ProcessStateType } from "../../components/Process/types";
import type { RootState } from "../index";

export const getProcessState = (state: RootState): ProcessStateType | null => state.scenarioState;

export const getIsRunning = createSelector(getProcessState, (state) => {
    return ["RUNNING", "SCHEDULED"].includes(state?.status?.name);
});

export const getIsDeploying = createSelector(getProcessState, (state) => {
    return ["DURING_DEPLOY"].includes(state?.status?.name);
});

export const getIsRedeploying = createSelector(getProcessState, (state) => {
    return ["DURING_REDEPLOY"].includes(state?.status?.name);
});
