import { Action, Reducer } from "../actions/reduxTypes";
import { ProcessStateType } from "../components/Process/types";

export const reducer: Reducer<ProcessStateType> = (state = null, action: Action): ProcessStateType => {
    switch (action.type) {
        case "DISPLAY_PROCESS":
            return action.scenario.state;
        case "PROCESS_STATE_LOADED":
            return action.processState;
        default:
            return state;
    }
};
