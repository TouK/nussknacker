import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";

import type { Reducer } from "../actions/reduxTypes";

type SettingsNames =
    // keep sorted
    | "assistant.includeScenarioData"
    | "assistant.showHelp"
    | "cloud.showIntegrationsCreators"
    | "debug.dontRenderCountsOnNodes"
    | "debug.forceDisableModals"
    | "debug.lightTheme"
    | "debug.nodesAsJson"
    | "editor.allowForceSwitch"
    | "editor.showRangeMessages"
    | "editor.showResetToDefaultButton"
    | "node.advancedStickyNotes"
    | "node.autoApply"
    | "node.shortCounts"
    | "node.showAggregateSwitcher"
    | "node.showFragmentCreator"
    | "node.showGenerateEndpointButton"
    | "node.showInputsAndOutputs"
    | "node.showMockFieldOnEnrichers"
    | "node.showSendRequestButton"
    | "scenario.allowQuickCancelDeploy"
    | "scenario.allowQuickDeploy"
    | "scenario.allowQuickSave"
    | "scenario.autoEnableLiveData"
    | "scenario.liveData.showNodeAnimations"
    | "scenario.liveData.showTransitionAnimations"
    | "scenario.showBreadcrumbs"
    | "toolbar.autoSaveDuringDeployRedeploy"
    | `editor.${string}.noWrap`
    | `editor.${string}.showLines`
    | `survey.${string}.closed`;

type ExtendRecordValue<T extends Record<string, any>, E> = {
    [K in keyof T]: T[K] | E;
};

export type UserSettings = Partial<Record<SettingsNames, boolean>>;
export type Setting = keyof UserSettings | NonNullable<string>;

export const getDefaultUserSettings = (initialUserFlags?: UserSettings): UserSettings => {
    const createFlag = (key: Setting, defaultValue = false): [Setting, boolean] => [key, initialUserFlags?.[key] ?? defaultValue];
    const entries = [
        // keep sorted
        createFlag("assistant.includeScenarioData"),
        createFlag("assistant.showHelp", true),
        createFlag("cloud.showIntegrationsCreators"),
        createFlag("debug.dontRenderCountsOnNodes"),
        createFlag("debug.forceDisableModals"),
        createFlag("debug.lightTheme"),
        createFlag("debug.nodesAsJson"),
        createFlag("editor.allowForceSwitch"),
        createFlag("editor.json.showLines", true),
        createFlag("editor.jsonTemplate.showLines", true),
        createFlag("editor.showRangeMessages"),
        createFlag("editor.showResetToDefaultButton"),
        createFlag("node.advancedStickyNotes"),
        createFlag("node.autoApply"),
        createFlag("node.shortCounts"),
        createFlag("node.showAggregateSwitcher"),
        createFlag("node.showFragmentCreator"),
        createFlag("node.showGenerateEndpointButton"),
        createFlag("node.showInputsAndOutputs"),
        createFlag("node.showMockFieldOnEnrichers"),
        createFlag("node.showSendRequestButton"),
        createFlag("scenario.allowQuickCancelDeploy"),
        createFlag("scenario.allowQuickDeploy"),
        createFlag("scenario.allowQuickSave"),
        createFlag("scenario.autoEnableLiveData"),
        createFlag("scenario.liveData.showNodeAnimations", true),
        createFlag("scenario.liveData.showTransitionAnimations", true),
        createFlag("scenario.showBreadcrumbs"),
        createFlag("toolbar.autoSaveDuringDeployRedeploy"),
    ];
    return Object.fromEntries(entries);
};

const reducer: Reducer<{ defaults: UserSettings; values: ExtendRecordValue<UserSettings, "default"> }> = (
    state = { defaults: getDefaultUserSettings(), values: {} },
    action,
) => {
    switch (action.type) {
        case "USERSETTING_SET":
            return {
                ...state,
                values: {
                    ...state.values,
                    [action.key]: action.value,
                },
            };
        case "RESET_TOOLBARS":
        case "USERSETTINGS_RESET":
            return {
                ...state,
                values: {},
            };
        case "USERSETTINGS_DEFAULTS_LOADED":
            return {
                ...state,
                defaults: action.settings,
            };
        default:
            return state;
    }
};

export const userSettings = persistReducer({ key: "settings", storage, blacklist: ["defaults"] }, reducer);
