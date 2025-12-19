import type { Reducer } from "../actions/reduxTypes";
import type { EditState } from "../components/graph/node-modal/node/useNodeState";

export type UiState = {
    isToolTipsHighlighted: boolean;
    pendingChanges: Record<string, EditState>;
    aiContextRawData?: string;
    isItSnowing: boolean;
};

const emptyUiState: UiState = {
    isToolTipsHighlighted: false,
    pendingChanges: {},
    aiContextRawData: "",
    isItSnowing: true,
};

const uiReducer: Reducer<UiState> = (state = emptyUiState, action) => {
    switch (action.type) {
        case "SWITCH_TOOL_TIPS_HIGHLIGHT":
            return {
                ...state,
                isToolTipsHighlighted: action.isHighlighted,
            };
        case "SET_PENDING_CHANGES": {
            const pendingChanges = { ...state.pendingChanges };
            if (action.pendingChanges) {
                pendingChanges[action.id] = action.pendingChanges;
            } else {
                delete pendingChanges[action.id];
            }
            return {
                ...state,
                pendingChanges,
            };
        }
        case "ASSISTANT_ASK":
            return {
                ...state,
                aiContextRawData: action.contextRawData,
            };
        case "CLEAR_PROCESS":
            return {
                ...state,
                aiContextRawData: "",
            };
        default:
            return state;
    }
};

export const reducer = uiReducer;
