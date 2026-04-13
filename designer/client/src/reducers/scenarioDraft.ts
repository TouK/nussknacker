import type { Reducer } from "../actions/reduxTypes";
import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioDraft } from "../draftStorage";
import type { ScenarioGraph } from "../types/scenarioGraph";

export type { ScenarioDraft } from "../draftStorage";

export type ScenarioDraftState = Record<string, ScenarioDraft>;

export const draftKey = (processName: ProcessName, baseVersionId: ProcessVersionId | null) => `${processName}::${baseVersionId ?? "null"}`;

export type ScenarioDraftActions =
    | { type: "SCENARIO_DRAFT_SET"; payload: ScenarioDraft }
    | { type: "SCENARIO_DRAFT_CLEAR"; processName: ProcessName; baseVersionId: ProcessVersionId | null }
    | { type: "SCENARIO_DRAFT_HYDRATE"; drafts: ScenarioDraftState }
    | { type: "APPLY_SCENARIO_DRAFT"; scenarioGraph: ScenarioGraph };

export const scenarioDraftSet = (payload: ScenarioDraft): ScenarioDraftActions => ({
    type: "SCENARIO_DRAFT_SET",
    payload,
});
export const scenarioDraftClear = (processName: ProcessName, baseVersionId: ProcessVersionId | null): ScenarioDraftActions => ({
    type: "SCENARIO_DRAFT_CLEAR",
    processName,
    baseVersionId,
});
export const scenarioDraftHydrate = (drafts: ScenarioDraftState): ScenarioDraftActions => ({ type: "SCENARIO_DRAFT_HYDRATE", drafts });
export const applyScenarioDraft = (scenarioGraph: ScenarioGraph): ScenarioDraftActions => ({
    type: "APPLY_SCENARIO_DRAFT",
    scenarioGraph,
});

export const scenarioDraft: Reducer<ScenarioDraftState> = (state = {}, action) => {
    switch (action.type) {
        case "SCENARIO_DRAFT_SET": {
            const k = draftKey(action.payload.id, action.payload.baseVersionId);
            return { ...state, [k]: action.payload };
        }
        case "SCENARIO_DRAFT_CLEAR": {
            const k = draftKey(action.processName, action.baseVersionId);
            if (!(k in state)) return state;
            const { [k]: _removed, ...rest } = state;
            return rest;
        }
        case "SCENARIO_DRAFT_HYDRATE":
            return action.drafts;
        default:
            return state;
    }
};
