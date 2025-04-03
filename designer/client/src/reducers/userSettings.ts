import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";

import type { Reducer } from "../actions/reduxTypes";
import { isDev } from "../devHelpers";

type SettingsNames =
    | `editor.${string}.showLines`
    | `editor.${string}.noWrap`
    | `survey.${string}.closed`
    | "node.showAggregateSwitcher"
    | "node.shortCounts"
    | "node.showInputsAndOutputs"
    | "node.showFragmentCreator"
    | "cloud.showIntegrationsCreators"
    | "cloud.showAiAssistant"
    | "debug.nodesAsJson"
    | "debug.forceDisableModals"
    | "debug.userSettingsVisible";

export type UserSettings = Partial<Record<SettingsNames, boolean>>;

const reducer: Reducer<UserSettings> = (
    state = {
        "node.showAggregateSwitcher": false,
        "node.shortCounts": false,
        "node.showInputsAndOutputs": false,
        "node.showFragmentCreator": false,
        "cloud.showIntegrationsCreators": false,
        "cloud.showAiAssistant": false,
        "debug.nodesAsJson": false,
        "debug.forceDisableModals": false,
        "debug.userSettingsVisible": isDev,
    },
    action,
) => {
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
