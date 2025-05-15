import { createSelector } from "reselect";

import type { ProcessStateType } from "../../components/Process/types";
import type { RootState } from "../index";

export const getProcessState = (state: RootState): ProcessStateType | null => state.scenarioState;

export const getIsRunning = createSelector(getProcessState, (state) => {
    return ["RUNNING", "SCHEDULED"].includes(state?.status?.name);
});
