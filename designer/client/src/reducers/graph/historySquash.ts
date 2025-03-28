import type { Reducer } from "../../actions/reduxTypes";
import type { GraphStateWithHistory } from "./reducer";

export const appendHistorySquashLogic: Reducer<GraphStateWithHistory> = (state, action) => {
    const { lastIndexes = [], ...passState } = state;
    switch (action.type) {
        case "SQUASH_HISTORY": {
            const { from, to = passState.past.length } = action;
            const squashedPast = passState.past.filter((p, i) => i <= from || to < i);
            return {
                ...passState,
                past: squashedPast,
                future: passState.past.length !== squashedPast.length ? [] : passState.future,
                lastIndexes,
            };
        }
        case "NODE_DETAILS_OPENED": {
            return {
                ...passState,
                lastIndexes: [...lastIndexes, passState.past.length],
            };
        }
        case "NODE_DETAILS_CLOSED": {
            return {
                ...passState,
                lastIndexes: lastIndexes.slice(0, lastIndexes.length - 1),
            };
        }
        default:
            return {
                ...passState,
                lastIndexes,
            };
    }
};

export type SquashHistoryActions = { type: "SQUASH_HISTORY"; from: number; to?: number };

export const squashHistory = (...range: [] | [number] | [number, number]) => {
    const [from = 0, to] = range.sort();
    return { type: "SQUASH_HISTORY", from, to };
};
