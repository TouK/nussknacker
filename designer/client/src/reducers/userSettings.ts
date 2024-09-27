import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";
import { Reducer } from "../actions/reduxTypes";

type SettingsNames =
    | `${string}.showLines`
    | `${string}.noWrap`
    | "node.shortCounts"
    | `survey-panel(${string}).closed`
    | "debug.nodesAsJson"
    | "debug.forceDisableModals";

export type UserSettings = Partial<Record<SettingsNames, boolean>>;

const reducer: Reducer<UserSettings> = (state = {}, action) => {
    switch (action.type) {
        case "SET_SETTINGS":
            return action.settings;
        case "TOGGLE_SETTINGS":
            return action.settings.reduce((value, key) => ({ ...value, [key]: !state[key] }), state);
        default:
            return state;
    }
};

export const userSettings = persistReducer({ key: `settings`, storage }, reducer);
