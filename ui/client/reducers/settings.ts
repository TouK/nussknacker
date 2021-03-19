import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"
import {ProcessDefinitionData} from "../types"

export enum AuthBackends {
  BASIC = "BasicAuth",
  OAUTH2 = "OAuth2",
  REMOTE = "Remote",
}

export type SettingsState = {
  loggedUser: Partial<User>,
  featuresSettings: $TodoType,
  authenticationSettings: AuthenticationSettings,
  analyticsSettings: $TodoType,
  processDefinitionData: ProcessDefinitionData,
  availableQueryableStates: $TodoType,
}

export type AuthenticationSettings = {
  backend?: AuthBackends,
  authorizeUrl?: string,
  jwtAuthServerPublicKey?: string,
  jwtIdTokenNonceVerificationRequired?: boolean,
  implicitGrantEnabled?: boolean,
}

const initialState: SettingsState = {
  loggedUser: {},
  featuresSettings: {},
  authenticationSettings: {},
  analyticsSettings: {},
  processDefinitionData: {},
  availableQueryableStates: {},
}

export function reducer(state: SettingsState = initialState, action: Action): SettingsState {
  switch (action.type) {
    case "LOGGED_USER": {
      const {user} = action
      return {
        ...state,
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
    case "PROCESS_DEFINITION_DATA": {
      return {
        ...state,
        processDefinitionData: action.processDefinitionData,
      }
    }
    case "AVAILABLE_QUERY_STATES": {
      return {
        ...state,
        availableQueryableStates: action.availableQueryableStates,
      }
    }
    default:
      return state
  }
}
