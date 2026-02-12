import { persistReducer } from "redux-persist";
import storage from "redux-persist/lib/storage";

import type { Reducer } from "../actions/reduxTypes";
import type { ExpressionLang } from "../components/graph/node-modal/editors/expression/types";
import type { Prettify } from "../components/graph/node-modal/useNodeTypeDetailsContentLogic";
import { SNOW_SNOW_FLAG } from "../containers/SnowSnow";

// empty default values, not visible on __settings until set
type DynamicSettingsNames =
    | `editor.${ExpressionLang}.noWrap`
    | `editor.${ExpressionLang}.showLines`
    | `survey.${string}.closed`
    | `assistant.tools.${string}.executeWithoutAsking`;

type DeferedString = string & NonNullable<unknown>;
type KeysOfEntries<T> = T extends Array<[infer U, boolean]> ? U : never;
type ExtendRecordValue<T extends Record<string, any>, E> = Prettify<{
    [K in keyof T]: T[K] | E;
}>;

export type UserSettings = Partial<Readonly<Prettify<Record<DynamicSettingsNames, boolean> & ReturnType<typeof getDefaultUserSettings>>>>;
export type Setting = NonNullable<keyof UserSettings> | NonNullable<string>;

export const getDefaultUserSettings = (initialUserFlags?: Record<string, boolean>) => {
    function createFlag<K extends DynamicSettingsNames | DeferedString>(key: K, defaultValue?: boolean): [K, boolean] {
        return [key, initialUserFlags?.[key] ?? defaultValue];
    }

    // TODO: include all from initialUserFlags
    const entries = [
        // keep sorted
        createFlag("assistant.includeScenarioData"),
        createFlag("assistant.showHelp", true),
        createFlag("cloud.showIntegrationsCreators"),
        createFlag("debug.dontRenderCountsOnNodes"),
        createFlag("debug.editor.forceSpelEditors"),
        createFlag("debug.forceDisableModals"),
        createFlag("debug.lightTheme"),
        createFlag("debug.nodesAsJson"),
        createFlag("debug.scenario.showNodeAlignToolbar"),
        createFlag("editor.allowForceSwitch"),
        createFlag("editor.json.showLines", true),
        createFlag("editor.jsonTemplate.showLines", true),
        createFlag("editor.showRangeMessages"),
        createFlag("editor.showResetToDefaultButton"),
        createFlag("node.advancedStickyNotes"),
        createFlag("node.autoApply"),
        createFlag("node.inputsAndOutputs.showBlinkAnimations", true),
        createFlag("node.shortCounts"),
        createFlag("node.showAggregateSwitcher"),
        createFlag("node.showFragmentCreator"),
        createFlag("node.showGenerateEndpointButton"),
        createFlag("node.showInputsAndOutputs"),
        createFlag("node.showMockFieldOnEnrichers"),
        createFlag("node.showSendRequestButton"),
        createFlag("node.showTestingTab"),
        createFlag("node.showTestConnectionButton", true),
        createFlag("scenario.allowQuickCancelDeploy"),
        createFlag("scenario.allowQuickDeploy"),
        createFlag("scenario.allowQuickSave"),
        createFlag("scenario.autoEnableLiveData"),
        createFlag("scenario.liveData.showNodeAnimations", true),
        createFlag("scenario.liveData.showTransitionAnimations", true),
        createFlag("scenario.showBreadcrumbs"),
        createFlag("toolbar.autoSaveDuringDeployRedeploy"),
        createFlag(SNOW_SNOW_FLAG, false),
    ];
    return Object.fromEntries(entries) as Readonly<Prettify<Record<KeysOfEntries<typeof entries>, boolean>>>;
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
        case "USERSETTING_DELETE": {
            const values = { ...state.values };
            delete values[action.key];
            return { ...state, values };
        }
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
