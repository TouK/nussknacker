import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"
import {ProcessDefinitionData} from "../types"

export type SettingsState = {
  loggedUser: Partial<User>,
  featuresSettings: $TodoType,
  authenticationSettings: AuthenticationSettings,
  analyticsSettings: $TodoType,
  processDefinitionData: ProcessDefinitionData,
  availableQueryableStates: $TodoType,
}

export type AuthenticationSettings = {
  backend: string,
  authorizeUrl?: URL,
  jwtAuthServerPublicKey?: string,
  jwtIdTokenNonceVerificationRequired?: boolean,
  implicitGrantEnabled?: boolean,
}

const initialState: SettingsState = {
  loggedUser: {},
  featuresSettings: {},
  authenticationSettings: {
    // eslint-disable-next-line i18next/no-literal-string
    backend: "BasicAuth",
  },
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
