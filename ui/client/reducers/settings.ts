import {$TodoType} from "../actions/migrationTypes"
import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"

type SettingsState = {
  loggedUser: Partial<User>;
  featuresSettings: $TodoType;
  authenticationSettings: $TodoType;
  analyticsSettings: $TodoType;
  processDefinitionData: $TodoType;
  availableQueryableStates: $TodoType;
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
