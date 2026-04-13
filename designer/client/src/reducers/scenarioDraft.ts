import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";

import type { Reducer } from "../actions/reduxTypes";
import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioGraph } from "../types/scenarioGraph";

export interface ScenarioDraft {
    processName: ProcessName;
    baseVersionId: ProcessVersionId | null;
    scenarioGraph: ScenarioGraph;
    updatedAt: string;
}

export type ScenarioDraftState = ScenarioDraft | null;

export type ScenarioDraftActions =
    | { type: "SCENARIO_DRAFT_SET"; payload: ScenarioDraft }
    | { type: "SCENARIO_DRAFT_CLEAR" }
    | { type: "APPLY_SCENARIO_DRAFT"; scenarioGraph: ScenarioGraph };

export const scenarioDraftSet = (payload: ScenarioDraft): ScenarioDraftActions => ({ type: "SCENARIO_DRAFT_SET", payload });
export const scenarioDraftClear = (): ScenarioDraftActions => ({ type: "SCENARIO_DRAFT_CLEAR" });
export const applyScenarioDraft = (scenarioGraph: ScenarioGraph): ScenarioDraftActions => ({
    type: "APPLY_SCENARIO_DRAFT",
    scenarioGraph,
});

const reducer: Reducer<ScenarioDraftState> = (state = null, action) => {
    switch (action.type) {
        case "SCENARIO_DRAFT_SET":
            return action.payload;
        case "SCENARIO_DRAFT_CLEAR":
            return null;
        default:
            return state;
    }
};

export const scenarioDraft = persistReducer({ key: "scenarioDraft", storage }, reducer);
