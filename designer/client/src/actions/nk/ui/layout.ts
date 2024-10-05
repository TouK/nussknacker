import { isEqual } from "lodash";
import { sortBy } from "lodash/fp";
import { getLayout } from "../../../reducers/selectors/layout";
import { NodeId } from "../../../types";
import { ThunkAction } from "../../reduxTypes";
import { WithConfigId } from "../toolbars";

export type Position = {
    x: number;
    y: number;
};
export type NodePosition = {
    id: NodeId;
    position: Position;
};
export type Layout = NodePosition[];
export type GraphLayoutFunction = () => void;

export type LayoutChangedAction = {
    layout: Layout;
    type: "LAYOUT_CHANGED";
};

export const enum PanelSide {
    Right = "RIGHT",
    Left = "LEFT",
}

type TogglePanelAction = {
    type: "TOGGLE_PANEL";
    side: PanelSide;
};

export type PanelActions = WithConfigId<TogglePanelAction>;

export function layoutChanged(layout?: Layout): ThunkAction {
    const sortById = sortBy<NodePosition>(({ id }) => id);
    return (dispatch, getState) => {
        const currentLayout = sortById(getLayout(getState()));
        if (layout) {
            const nextLayout = sortById(layout);
            if (!isEqual(currentLayout, nextLayout)) {
                dispatch({
                    type: "LAYOUT_CHANGED",
                    layout: nextLayout,
                });
            }
        } else {
            dispatch({
                type: "LAYOUT_CHANGED",
                layout: currentLayout,
            });
        }
    };
}

export function layout(graphLayoutFunction: GraphLayoutFunction): ThunkAction {
    return (dispatch) => {
        graphLayoutFunction();

        return dispatch({
            type: "LAYOUT",
        });
    };
}
