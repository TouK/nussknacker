import type { Reducer } from "../actions/reduxTypes";
import type { ScenarioDraftState } from "../actions/scenarioDraftActions";
import type { ProcessName, ProcessVersionId } from "../components/Process/types";
import type { ScenarioDraft } from "../draftStorage";

export type { ScenarioDraft } from "../draftStorage";

export const draftKey = (processName: ProcessName, baseVersionId: ProcessVersionId | null) => `${processName}::${baseVersionId ?? "null"}`;

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
