import { PanelSide } from "../actions/nk";
import { Reducer } from "../actions/reduxTypes";

export type Panels = Record<PanelSide, boolean>;

export const defaultState: Panels = {
    LEFT: true,
    RIGHT: true,
};

export const panels: Reducer<Panels> = (state = defaultState, action) => {
    switch (action.type) {
        case "TOGGLE_PANEL":
            return {
                ...state,
                [action.side]: state[action.side] == false,
            };

        case "RESET_TOOLBARS":
            return defaultState;

        default:
            return state;
    }
};
