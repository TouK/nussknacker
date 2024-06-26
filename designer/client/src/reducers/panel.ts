import { Reducer } from "../actions/reduxTypes";
import { PanelSide } from "../actions/nk";

export type Panels = Record<PanelSide, boolean>;

const defaultState: Panels = {
    LEFT: true,
    RIGHT: true,
};

export const panels: Reducer<Panels> = (state = defaultState, action) => {
    switch (action.type) {
        case "TOGGLE_PANEL":
            return {
                ...state,
                [action.side]: !state[action.side],
            };

        case "RESET_TOOLBARS":
            return defaultState;

        default:
            return state;
    }
};
