import { produce } from "immer";
import { merge } from "lodash";

import type { Reducer } from "../actions/reduxTypes";
import type { ProcessStateType } from "../components/Process/types";

export const reducer: Reducer<ProcessStateType> = produce((draft, action) => {
    switch (action.type) {
        case "DISPLAY_PROCESS":
            // Since scenario endpoint doesn't return null attributes the state will be undefined for fragments.
            // Redux does not allow to return undefined values so in that case we return null explicitly.
            merge(draft, action.scenario.state ?? null);
            break;
        case "PROCESS_STATE_LOADED":
            merge(draft, action.processState);
            break;
    }
}, null);
