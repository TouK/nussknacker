import { Action, Reducer } from "../actions/reduxTypes";
import { ProcessStateType } from "../components/Process/types";

export const reducer: Reducer<ProcessStateType> = (state = null, action: Action): ProcessStateType => {
    switch (action.type) {
        case "DISPLAY_PROCESS":
            // Since scenario endpoint doesn't return null attributes the state will be undefined for fragments.
            // Redux does not allow to return undefined values so in that case we return null explicitly.
            return action.scenario.state ?? null;
        case "PROCESS_STATE_LOADED":
            return action.processState;
        default:
            return state;
    }
};
