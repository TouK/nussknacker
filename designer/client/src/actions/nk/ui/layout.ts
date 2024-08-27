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
    return (dispatch, getState) => {
        dispatch({
            type: "LAYOUT_CHANGED",
            layout: layout || getLayout(getState()),
        });
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
