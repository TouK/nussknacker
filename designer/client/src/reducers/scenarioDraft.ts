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

export type ScenarioDraftState = Record<string, ScenarioDraft>;

export const draftKey = (processName: ProcessName, baseVersionId: ProcessVersionId | null) =>
    `${processName}::${baseVersionId ?? "null"}`;

export type ScenarioDraftActions =
    | { type: "SCENARIO_DRAFT_SET"; payload: ScenarioDraft }
    | { type: "SCENARIO_DRAFT_CLEAR"; processName: ProcessName; baseVersionId: ProcessVersionId | null }
    | { type: "APPLY_SCENARIO_DRAFT"; scenarioGraph: ScenarioGraph };

export const scenarioDraftSet = (payload: ScenarioDraft): ScenarioDraftActions => ({ type: "SCENARIO_DRAFT_SET", payload });
export const scenarioDraftClear = (processName: ProcessName, baseVersionId: ProcessVersionId | null): ScenarioDraftActions => ({
    type: "SCENARIO_DRAFT_CLEAR",
    processName,
    baseVersionId,
});
export const applyScenarioDraft = (scenarioGraph: ScenarioGraph): ScenarioDraftActions => ({
    type: "APPLY_SCENARIO_DRAFT",
    scenarioGraph,
});

const reducer: Reducer<ScenarioDraftState> = (state = {}, action) => {
    switch (action.type) {
        case "SCENARIO_DRAFT_SET": {
            const k = draftKey(action.payload.processName, action.payload.baseVersionId);
            return { ...state, [k]: action.payload };
        }
        case "SCENARIO_DRAFT_CLEAR": {
            const k = draftKey(action.processName, action.baseVersionId);
            if (!(k in state)) return state;
            const { [k]: _removed, ...rest } = state;
            return rest;
        }
        default:
            return state;
    }
};

export const scenarioDraft = persistReducer({ key: "scenarioDraft", storage }, reducer);
