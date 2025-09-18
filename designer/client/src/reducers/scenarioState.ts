import { produce } from "immer";

import type { Reducer } from "../actions/reduxTypes";
import type { ProcessStateType } from "../components/Process/types";
import { KnownStatusName, PredefinedActionName } from "../components/Process/types";

export const reducer: Reducer<ProcessStateType> = produce((draft, action) => {
    switch (action.type) {
        case "DISPLAY_PROCESS": {
            // Since scenario endpoint doesn't return null attributes the state will be undefined for fragments.
            // Redux and Immer does not allow to return undefined values so in that case we return null explicitly.
            if (action.scenario.state) {
                return action.scenario.state;
            }
            return null;
        }
        case "PENDING_SCENARIO_ACTION": {
            switch (action.action) {
                case PredefinedActionName.Deploy:
                    draft.status.name = KnownStatusName.Deploying;
                    break;
                case PredefinedActionName.Redeploy:
                    draft.status.name = KnownStatusName.Redeploying;
                    break;
            }
            return draft;
        }
        case "PROCESS_STATE_LOADED": {
            return action.processState;
        }
        case "CLEAR_PROCESS": {
            return {} as ProcessStateType;
        }
    }
    return draft;
}, {} as ProcessStateType);
