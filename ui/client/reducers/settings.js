// @flow

import type {Action} from "../actions/reduxTypes.flow"
import User from "../common/models/User"

type SettingsState = {
  loggedUser: $Shape<User>,
  featuresSettings: $FlowTODO,
  authenticationSettings: $FlowTODO,
  analyticsSettings: $FlowTODO,
  processDefinitionData: $FlowTODO,
  availableQueryableStates: $FlowTODO,
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