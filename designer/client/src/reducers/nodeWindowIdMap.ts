import type { Action } from "../actions/reduxTypes";
import type { NodeId } from "../types/node";

// node's name is node's id so we have to map when changed to avoid duplicates
export function nodeWindowIdMap(state = {}, action: Action): Record<NodeId, string> {
    switch (action.type) {
        case "NODE_DETAILS_OPENED":
            return {
                ...state,
                [action.nodeId]: action.windowId,
            };
        case "NODE_DETAILS_CLOSED":
        case "NODE_DETAILS_RELOAD":
            return {
                ...state,
                [action.nodeId]: undefined,
                [state[action.nodeId]]: undefined,
            };
        case "EDIT_NODE":
            return {
                ...state,
                [action.before.id]: action.after.id,
                [action.after.id]: state[action.before.id],
            };
    }
    return state;
}
