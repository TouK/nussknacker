import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";

import type { Reducer } from "../actions/reduxTypes";

type SettingsNames =
    | "assistant.includeScenarioData"
    | "assistant.showHelp"
    | "cloud.showIntegrationsCreators"
    | "debug.forceDisableModals"
    | "debug.lightTheme"
    | "debug.nodesAsJson"
    | "editor.showRangeMessages"
    | "editor.showResetToDefaultButton"
    | "editor.allowForceSwitch"
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
    | "scenario.showBreadcrumbs"
    | "scenario.showLiveDataAnimations"
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
    return Object.fromEntries([
        createFlag("assistant.includeScenarioData"),
        createFlag("assistant.showHelp", true),
        createFlag("cloud.showIntegrationsCreators"),
        createFlag("debug.forceDisableModals"),
        createFlag("debug.lightTheme"),
        createFlag("debug.nodesAsJson"),
        createFlag("editor.json.showLines", true),
        createFlag("editor.jsonTemplate.showLines", true),
        createFlag("editor.showRangeMessages"),
        createFlag("editor.showResetToDefaultButton"),
        createFlag("editor.allowForceSwitch"),
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
        createFlag("scenario.showBreadcrumbs"),
        createFlag("scenario.showLiveDataAnimations", true),
        createFlag("toolbar.autoSaveDuringDeployRedeploy"),
    ]);
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
