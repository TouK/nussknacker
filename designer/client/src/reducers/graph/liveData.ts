import type { Initiator } from "../../actions/nk/liveData";
import type { Reducer } from "../../actions/reduxTypes";

export type LiveData = {
    working?: boolean;
    pauseReasons?: Initiator[];
    last?: number;
    nextIn?: number;
};

export const liveData: Reducer<LiveData> = (state = {}, action) => {
    switch (action.type) {
        case "DISPLAY_PROCESS_COUNTS": {
            return {
                ...state,
                nextIn: null,
            };
        }
        case "DISPLAY_LIVE_DATA": {
            return {
                ...state,
                nextIn: action.nextIn,
                last: new Date(action.results.timestamp).getTime(),
            };
        }
        case "LIVE_DATA_STOP": {
            return {
                ...state,
                nextIn: null,
                working: false,
                pauseReasons: action.initiator
                    ? [...(state.pauseReasons || []).filter((r) => r !== action.initiator), action.initiator]
                    : state.pauseReasons,
            };
        }
        case "LIVE_DATA_START": {
            return {
                ...state,
                pauseReasons: action.initiator ? (state.pauseReasons || []).filter((r) => r !== action.initiator) : [],
            };
        }
        case "LIVE_DATA_STARTED": {
            return {
                ...state,
                working: true,
            };
        }
        case "HIDE_RUN_PROCESS_DETAILS": {
            return {
                ...state,
                nextIn: null,
                working: false,
            };
        }
        default:
            return state;
    }
};
