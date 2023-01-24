import {FeaturesSettings} from "../actions/nk"
import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"
import {ProcessDefinitionData} from "../types"

export enum AuthStrategy {
  BROWSER = "Browser",
  OAUTH2 = "OAuth2",
  REMOTE = "Remote", // Perhaps this should be named "Federated", "External" or "Module"?
}

export type SettingsState = {
  loggedUser: Partial<User>,
  featuresSettings: Partial<FeaturesSettings>,
  authenticationSettings: AuthenticationSettings,
  analyticsSettings: $TodoType,
  processDefinitionData: ProcessDefinitionData,
}

export type BaseAuthenticationSettings = {
  provider?: string,
  strategy?: string,
  anonymousAccessAllowed?: boolean,
}

export type AuthenticationSettings =
  BaseAuthenticationSettings
  | BrowserAuthenticationSettings
  | RemoteAuthenticationSettings
  | OAuth2Settings

export type BrowserAuthenticationSettings = {
  strategy: AuthStrategy.BROWSER,
} & BaseAuthenticationSettings

export type RemoteAuthenticationSettings = {
  strategy: AuthStrategy.REMOTE,
  moduleUrl?: string,
} & BaseAuthenticationSettings

export type OAuth2Settings = {
  strategy: AuthStrategy.OAUTH2,
  authorizeUrl?: string,
  jwtAuthServerPublicKey?: string,
  jwtIdTokenNonceVerificationRequired?: boolean,
  implicitGrantEnabled?: boolean,
} & BaseAuthenticationSettings

const initialState: SettingsState = {
  loggedUser: {},
  featuresSettings: {},
  authenticationSettings: {},
  analyticsSettings: {},
  processDefinitionData: {},
}

export function reducer(state: SettingsState = initialState, action: Action): SettingsState {
  switch (action.type) {
    case "LOGGED_USER": {
      const {user} = action
      return {
        ...state,
        //FIXME: remove class from store - plain data only
        loggedUser: user,
      }
    }
    case "UI_SETTINGS": {
      return {
        ...state,
        featuresSettings: action.settings.features,
        authenticationSettings: action.settings.authentication,
        analyticsSettings: action.settings.analytics,
      }
    }
    default:
      return state
  }
}
