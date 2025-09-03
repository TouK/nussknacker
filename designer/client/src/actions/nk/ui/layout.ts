import { isEqual, sortBy } from "lodash";

import { getLayout } from "../../../reducers/selectors/layout";
import type { NodeId } from "../../../types";
import type { ThunkAction } from "../../reduxTypes";

export type Position = {
    x: number;
    y: number;
};

export const snapToInt = ({ x, y }: Position): Position => ({
    x: Math.round(x),
    y: Math.round(y),
});

export type NodePosition = {
    id: NodeId;
    position: Position;
};
export type Layout = NodePosition[];
export type LayoutActions = { type: "LAYOUT" } | { type: "LAYOUT_CHANGED"; layout: Layout } | { type: "LAYOUT_RELOADED"; layout: Layout };

export function layoutChanged(layout?: Layout): ThunkAction {
    return (dispatch, getState) => {
        const oldLayout = getLayout(getState());
        const newLayout = sortBy(layout || [], (e) => e.id)
            .map(({ id, position }) => ({ id, position: snapToInt(position) }))
            .filter(({ id, position }) => id && position);

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
