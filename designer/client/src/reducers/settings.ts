import { FeaturesSettings } from "../actions/nk";
import { Action } from "../actions/reduxTypes";
import User from "../common/models/User";
import { DEV_TOOLBARS } from "../components/toolbarSettings/DEV_TOOLBARS";
import { ToolbarsConfig } from "../components/toolbarSettings/types";
import { ProcessDefinitionData } from "../types";
import { WithId } from "../types/common";
import { ToolbarsSide } from "./toolbars";

export enum AuthStrategy {
    BROWSER = "Browser",
    OAUTH2 = "OAuth2",
    REMOTE = "Remote", // Perhaps this should be named "Federated", "External" or "Module"?
}

export type SettingsState = {
    loggedUser: Partial<User>;
    featuresSettings: Partial<FeaturesSettings>;
    authenticationSettings: AuthenticationSettings;
    processDefinitionData: ProcessDefinitionData;
    processToolbarsConfiguration: WithId<ToolbarsConfig>;
};

export type BaseAuthenticationSettings = {
    provider?: string;
    strategy?: string;
    anonymousAccessAllowed?: boolean;
};

export type AuthenticationSettings =
    | BaseAuthenticationSettings
    | BrowserAuthenticationSettings
    | RemoteAuthenticationSettings
    | OAuth2Settings;

export type BrowserAuthenticationSettings = {
    strategy: AuthStrategy.BROWSER;
} & BaseAuthenticationSettings;

export type RemoteAuthenticationSettings = {
    strategy: AuthStrategy.REMOTE;
    moduleUrl?: string;
} & BaseAuthenticationSettings;

export type OAuth2Settings = {
    strategy: AuthStrategy.OAUTH2;
    authorizeUrl?: string;
    jwtAuthServerPublicKey?: string;
    jwtIdTokenNonceVerificationRequired?: boolean;
    implicitGrantEnabled?: boolean;
} & BaseAuthenticationSettings;

const initialState: SettingsState = {
    loggedUser: {},
    featuresSettings: {},
    authenticationSettings: {},
    processDefinitionData: {
        edgesForNodes: [],
    },
    processToolbarsConfiguration: null,
};

export function reducer(state: SettingsState = initialState, action: Action): SettingsState {
    switch (action.type) {
        case "LOGGED_USER": {
            const { user } = action;
            return {
                ...state,
                //FIXME: remove class from store - plain data only
                loggedUser: user,
            };
        }
        case "UI_SETTINGS": {
            return {
                ...state,
                featuresSettings: action.settings.features,
                authenticationSettings: action.settings.authentication,
            };
        }
        case "PROCESS_DEFINITION_DATA": {
            const { processDefinitionData } = action;
            return {
                ...state,
                processDefinitionData,
            };
        }
        case "PROCESS_TOOLBARS_CONFIGURATION_LOADED": {
            return {
                ...state,
                processToolbarsConfiguration: {
                    ...action.data,
                    [ToolbarsSide.TopRight]: [{ id: "survey-panel" }, ...action.data.topRight],
                    [ToolbarsSide.BottomRight]: [...action.data.bottomRight, ...DEV_TOOLBARS],
                },
            };
        }
        default:
            return state;
    }
}
