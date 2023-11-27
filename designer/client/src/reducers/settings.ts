import { FeaturesSettings } from "../actions/nk";
import { Action } from "../actions/reduxTypes";
import User from "../common/models/User";
import { DEV_TOOLBARS } from "../components/toolbarSettings/DEV_TOOLBARS";
import { ProcessDefinitionData } from "../types";
import { WithId } from "../types/common";
import { ToolbarsConfig } from "../components/toolbarSettings/types";
import { ToolbarsSide } from "./toolbars";
import { WIP_TOOLBARS } from "../components/toolbarSettings/WIP_TOOLBARS";

export enum AuthStrategy {
    BROWSER = "Browser",
    OAUTH2 = "OAuth2",
    REMOTE = "Remote", // Perhaps this should be named "Federated", "External" or "Module"?
}

export type SettingsState = {
    loggedUser: Partial<User>;
    featuresSettings: Partial<FeaturesSettings>;
    authenticationSettings: AuthenticationSettings;
    analyticsSettings: $TodoType;
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
    analyticsSettings: {},
    processDefinitionData: {},
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
                analyticsSettings: action.settings.analytics,
            };
        }
        case "PROCESS_DEFINITION_DATA": {
            const { processDefinitionData } = action;
            const { processDefinition, componentGroups } = processDefinitionData;
            const { customStreamTransformers } = processDefinition;
            return {
                ...state,
                processDefinitionData: {
                    ...processDefinitionData,
                    componentGroups: [
                        ...componentGroups,
                        {
                            name: "_debug",
                            components: [
                                {
                                    type: "customNode",
                                    label: "decisionTableCustomNode",
                                    node: {
                                        id: "",
                                        type: "CustomNode",
                                        nodeType: "table",
                                        parameters: [],
                                    },
                                    categories: ["Category1", "Category2", "DemoFeatures"],
                                    branchParametersTemplate: [],
                                },
                            ],
                        },
                    ],
                    processDefinition: {
                        ...processDefinition,
                        customStreamTransformers: {
                            ...customStreamTransformers,
                            table: {
                                parameters: [
                                    {
                                        name: "tableData",
                                        typ: {
                                            display: "Unknown",
                                            type: "Unknown",
                                            refClazzName: "java.lang.Object",
                                            params: [],
                                        },
                                        editor: {
                                            simpleEditor: {
                                                type: "TableEditor",
                                            },
                                            defaultMode: "SIMPLE",
                                            type: "DualParameterEditor",
                                        },
                                        validators: [],
                                        defaultValue: {
                                            language: "spel",
                                            expression: `
{
    columns: {{'key'}, {'value'}},
    rows: {
        {'', ''}
    }
}
`,
                                        },
                                        additionalVariables: {},
                                        variablesToHide: [],
                                        branchParam: false,
                                    },
                                ],
                                returnType: null,
                            },
                        },
                    },
                },
            };
        }
        case "PROCESS_TOOLBARS_CONFIGURATION_LOADED": {
            return {
                ...state,
                processToolbarsConfiguration: {
                    ...action.data,
                    [ToolbarsSide.TopRight]: [...WIP_TOOLBARS, ...action.data.topRight],
                    [ToolbarsSide.BottomRight]: [...action.data.bottomRight, ...DEV_TOOLBARS],
                },
            };
        }
        default:
            return state;
    }
}
