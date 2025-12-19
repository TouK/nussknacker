import { persistReducer, createTransform } from "redux-persist";
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
    | "node.autoApply"
    | "node.showGenerateEndpointButton"
    | "node.showSendRequestButton"
    | "node.showMockFieldOnEnrichers"
    | "cloud.showIntegrationsCreators"
    | "debug.nodesAsJson"
    | "debug.forceDisableModals"
    | "debug.userSettingsVisible"
    | "scenario.allowQuickSave"
    | "scenario.allowQuickDeploy"
    | "scenario.allowQuickCancelDeploy"
    | "scenario.showBreadcrumbs"
    | "scenario.autoEnableLiveData"
    | "scenario.showLiveDataAnimations"
    | "scenario.isItSnowing"
    | "editor.showRangeMessages"
    | "toolbar.autoSaveDuringDeployRedeploy"
    | "editor.showResetToDefaultButton";

export type UserSettings = Partial<Record<SettingsNames, boolean>>;

const getInitialUserFlag = (flagName: SettingsNames, defaultValue = false): boolean => {
    return window?.["$initialUserFlags"]?.[flagName] ?? defaultValue;
};

const getDefaultUserSettings = (): UserSettings => ({
    "node.showAggregateSwitcher": getInitialUserFlag("node.showAggregateSwitcher"),
    "node.shortCounts": getInitialUserFlag("node.shortCounts"),
    "node.showInputsAndOutputs": getInitialUserFlag("node.showInputsAndOutputs"),
    "node.showFragmentCreator": getInitialUserFlag("node.showFragmentCreator"),
    "node.showGenerateEndpointButton": getInitialUserFlag("node.showGenerateEndpointButton"),
    "node.showSendRequestButton": getInitialUserFlag("node.showSendRequestButton"),
    "node.autoApply": getInitialUserFlag("node.autoApply"),
    "node.showMockFieldOnEnrichers": getInitialUserFlag("node.showMockFieldOnEnrichers"),
    "cloud.showIntegrationsCreators": getInitialUserFlag("cloud.showIntegrationsCreators"),
    "debug.nodesAsJson": getInitialUserFlag("debug.nodesAsJson"),
    "debug.forceDisableModals": getInitialUserFlag("debug.forceDisableModals"),
    "debug.userSettingsVisible": getInitialUserFlag("debug.userSettingsVisible", isDev),
    "editor.jsonTemplate.showLines": getInitialUserFlag("editor.jsonTemplate.showLines", true),
    "scenario.allowQuickSave": getInitialUserFlag("scenario.allowQuickSave"),
    "scenario.allowQuickDeploy": getInitialUserFlag("scenario.allowQuickDeploy"),
    "scenario.allowQuickCancelDeploy": getInitialUserFlag("scenario.allowQuickCancelDeploy"),
    "scenario.showBreadcrumbs": getInitialUserFlag("scenario.showBreadcrumbs"),
    "scenario.autoEnableLiveData": getInitialUserFlag("scenario.autoEnableLiveData", false),
    "scenario.showLiveDataAnimations": getInitialUserFlag("scenario.showLiveDataAnimations", true),
    "scenario.isItSnowing": getInitialUserFlag("scenario.isItSnowing", false),
    "toolbar.autoSaveDuringDeployRedeploy": getInitialUserFlag("toolbar.autoSaveDuringDeployRedeploy", false),
    "editor.showRangeMessages": getInitialUserFlag("editor.showRangeMessages"),
    "editor.showResetToDefaultButton": getInitialUserFlag("editor.showResetToDefaultButton"),
});

/**
 * @desc The idea is to get default values from the global config and then use them in the reducer unless the user changed the setting manually; in this case, we take a value from local storage.
 * 1. We want to persist in a user setting state in localstorage only if the user has changed it.
 * 2. We don't want to persist the default values.
 * 3. When the value set by the user is the same as the default value, we don't want to persist it.
 */
const filterInitialValuesTransform = createTransform(
    (inboundState, key) => {
        const persistedState = localStorage.getItem("persist:settings");
        const defaults = getDefaultUserSettings();

        const valueFromLocalStorage = persistedState?.[key];
        if (valueFromLocalStorage) {
            return valueFromLocalStorage;
        }

        const valueSetByTheUserManually = defaults[key] !== inboundState;

        if (valueSetByTheUserManually) {
            return inboundState;
        }
        return undefined;
    },
    (outboundState) => outboundState,
);

const reducer: Reducer<UserSettings> = (state = getDefaultUserSettings(), action) => {
    switch (action.type) {
        case "SET_SETTINGS":
            return action.settings;
        case "TOGGLE_SETTINGS":
            return action.settings.reduce((value, key) => ({ ...value, [key]: !state[key] }), state);
        case "RESET_TOOLBARS":
            return { ...state, ...getDefaultUserSettings() };
        default:
            return state;
    }
};

export const userSettings = persistReducer({ key: "settings", storage, transforms: [filterInitialValuesTransform] }, reducer);
