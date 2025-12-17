import { Initiator } from "../../actions/nk/liveData";
import type { Reducer } from "../../actions/reduxTypes";

export type LiveData = {
    working?: boolean;
    pauseReasons?: Initiator[];
    last?: number;
    nextIn?: number;
};

export const liveData: Reducer<LiveData> = (state = { pauseReasons: [Initiator.init] }, action) => {
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
        case "CLEAR_PROCESS": {
            return {
                ...state,
                pauseReasons: [],
            };
        }
        case "NODE_DETAILS_CLOSED": {
            const toClean = [Initiator.list];
            return {
                ...state,
                pauseReasons: (state.pauseReasons || []).filter((r) => !toClean.includes(r)),
            };
        }
        case "LIVE_DATA_START": {
            const toClean = action.initiator ? [action.initiator] : state.pauseReasons;
            return {
                ...state,
                pauseReasons: (state.pauseReasons || []).filter((r) => !toClean.includes(r)),
            };
        }
        case "LIVE_DATA_STARTED": {
            return {
                ...state,
                working: true,
            };
        }
        default:
            return state;
    }
};
