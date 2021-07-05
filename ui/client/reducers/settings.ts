import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"
import {DEV_TOOLBARS} from "../components/toolbarSettings/defaultToolbarsConfig"
import {ProcessDefinitionData} from "../types"
import {WithId} from "../types/common"
import {ToolbarsConfig} from "../components/toolbarSettings/types"
import {ToolbarsSide} from "./toolbars"

export enum AuthBackends {
  BASIC = "BasicAuth",
  OAUTH2 = "OAuth2",
  REMOTE = "Remote",
  OTHER = "Other",
}

export type SettingsState = {
  loggedUser: Partial<User>,
  featuresSettings: $TodoType,
  authenticationSettings: AuthenticationSettings,
  analyticsSettings: $TodoType,
  processDefinitionData: ProcessDefinitionData,
  availableQueryableStates: $TodoType,
  processToolbarsConfiguration: WithId<ToolbarsConfig>,
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
  processToolbarsConfiguration: null,
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
    case "PROCESS_TOOLBARS_CONFIGURATION_LOADED": {
      return {
        ...state,
        processToolbarsConfiguration: {...action.data, [ToolbarsSide.BottomRight]: [...action.data.bottomRight, ...DEV_TOOLBARS]},
      }
    }
    default:
      return state
  }
}
