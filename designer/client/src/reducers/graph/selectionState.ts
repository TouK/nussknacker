import { uniq, xor } from "lodash";
import { Reducer } from "../../actions/reduxTypes";

export const selectionState: Reducer<string[]> = (state = [], action) => {
    switch (action.type) {
        case "NODES_WITH_EDGES_ADDED":
        case "NODE_ADDED":
            return action.nodes.map((n) => n.id);
        case "DELETE_NODES":
            return xor(state, action.ids);
        case "EXPAND_SELECTION":
            return uniq([...state, ...action.nodeIds]);
        case "TOGGLE_SELECTION":
            return xor(state, action.nodeIds);
        case "RESET_SELECTION":
            return action.nodeIds ? action.nodeIds : [];
    }
    return state;
};
