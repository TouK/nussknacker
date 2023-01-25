import {FeaturesSettings, SettingsData} from "../../types/settings"
import User from "../common/models/User"
import {combineReducers} from "redux"

export enum AuthStrategy {
  BROWSER = "Browser",
  OAUTH2 = "OAuth2",
  REMOTE = "Remote", // Perhaps this should be named "Federated", "External" or "Module"?
}

export type SettingsState = {
  loggedUser: Partial<User>,
  featuresSettings: Partial<FeaturesSettings>,
  authenticationSettings: AuthenticationSettings,
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

export enum ActionType {
  loggedUser = "LOGGED_USER",
  settings = "UI_SETTINGS"
}

type Action =
  | { type: ActionType.loggedUser, user: User }
  | { type: ActionType.settings, settings: SettingsData }

function authenticationSettings(state = {}, action: Action) {
  switch (action.type) {
    case ActionType.settings: {
      return action.settings.authentication
    }
    default:
      return state
  }
}

function featuresSettings(state = {}, action: Action) {
  switch (action.type) {
    case ActionType.settings: {
      return action.settings.features
    }
    default:
      return state
  }
}

function loggedUser(state = {}, action: Action) {
  switch (action.type) {
    case ActionType.loggedUser: {
      return action.user
    }
    default:
      return state
  }
}

export const reducer = combineReducers<SettingsState>({
  loggedUser,
  featuresSettings,
  authenticationSettings,
})
