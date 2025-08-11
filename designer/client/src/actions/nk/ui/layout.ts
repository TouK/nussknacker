import { isEqual, sortBy } from "lodash";

import { getLayout } from "../../../reducers/selectors/layout";
import type { NodeId } from "../../../types";
import type { ThunkAction } from "../../reduxTypes";

export type Position = {
    x: number;
    y: number;
};
export type NodePosition = {
    id: NodeId;
    position: Position;
};
export type Layout = NodePosition[];
export type LayoutActions = { type: "LAYOUT" } | { type: "LAYOUT_CHANGED"; layout: Layout } | { type: "LAYOUT_RELOADED"; layout: Layout };

export function layoutChanged(layout?: Layout): ThunkAction {
    return (dispatch, getState) => {
        const newLayout = sortBy(layout || [], (e) => e.id);
        const oldLayout = sortBy(getLayout(getState()) || [], (e) => e.id);

        if (newLayout.length < 1 || isEqual(newLayout, oldLayout)) {
            return dispatch({ type: "LAYOUT_RELOADED", layout: oldLayout });
        }

        dispatch({ type: "LAYOUT_CHANGED", layout: newLayout });
    };
}

export function layout(graphLayoutFunction: () => void): ThunkAction {
    return (dispatch) => {
        graphLayoutFunction();

        return dispatch({ type: "LAYOUT" });
    };
}
