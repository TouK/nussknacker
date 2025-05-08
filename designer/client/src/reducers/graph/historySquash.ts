import type { Action, Reducer, ThunkAction } from "../../actions/reduxTypes";
import { getHistoryCounts } from "../selectors/getHistory";
import type { GraphStateWithHistory } from "./reducer";

export const appendHistorySquashLogic: Reducer<GraphStateWithHistory> = (state, action) => {
    const { snapshots = [], ...passState } = state;
    switch (action.type) {
        case "SQUASH_HISTORY": {
            const { from, to = passState.past.length } = action;
            const squashedPast = passState.past.filter((p, i) => i <= from || to < i);
            return {
                ...passState,
                past: squashedPast,
                future: passState.past.length !== squashedPast.length ? [] : passState.future,
                snapshots,
            };
        }
        case "TAKE_HISTORY_SNAPSHOT": {
            return {
                ...passState,
                snapshots: [...snapshots, passState.past.length],
            };
        }
        case "REMOVE_HISTORY_SNAPSHOT": {
            return {
                ...passState,
                snapshots: snapshots.slice(0, snapshots.length - 1),
            };
        }
        default:
            return { ...passState, snapshots };
    }
};

export type SquashHistoryActions =
    | { type: "TAKE_HISTORY_SNAPSHOT" }
    | { type: "REMOVE_HISTORY_SNAPSHOT" }
    | { type: "SQUASH_HISTORY"; from: number; to?: number };

const squashHistory = (...range: [] | [number] | [number, number]): Action => {
    const [from = 0, to] = range.sort();
    return { type: "SQUASH_HISTORY", from, to };
};

export const takeHistorySnapshot = (): Action => ({ type: "TAKE_HISTORY_SNAPSHOT" });
export const removeHistorySnapshot = (): ThunkAction => {
    return (dispatch, getState) => {
        const [, , snapshot] = getHistoryCounts(getState());
        if (snapshot >= 0) {
            dispatch(squashHistory(snapshot));
            dispatch({ type: "REMOVE_HISTORY_SNAPSHOT" });
        }
    };
};
