import type { Reducer } from "../actions/reduxTypes";
import type { EditState } from "../components/graph/node-modal/node/useNodeState";
import { mergeReducers } from "./mergeReducers";

export type UiState = {
    isToolTipsHighlighted: boolean;
    pendingChanges: Record<string, EditState>;
};

const emptyUiState: UiState = {
    isToolTipsHighlighted: false,
    pendingChanges: {},
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
        default:
            return state;
    }
};

export const reducer = mergeReducers<UiState>(uiReducer);
