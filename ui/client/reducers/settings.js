const initialState = {
  loggedUser: {},
  featuresSettings: {},
  authenticationSettings: {},
  analyticsSettings: {},
  processStatesSettings: {}
};

export function reducer(state = initialState, action) {
  switch (action.type) {
    case "LOGGED_USER": {
      return {
        ...state,
        loggedUser: action.user,
      }
    }
    case "UI_SETTINGS": {
      return {
        ...state,
        featuresSettings: action.settings.features,
        authenticationSettings: action.settings.authentication,
        analyticsSettings: action.settings.analytics,
        processStatesSettings: action.settings.processStates
      }
    }
    case "PROCESS_DEFINITION_DATA": {
      return {
        ...state,
          processDefinitionData: action.processDefinitionData
      }
    }
    case "AVAILABLE_QUERY_STATES": {
      return {
        ...state,
        availableQueryableStates: action.availableQueryableStates
      }
    }
    default:
      return state
  }
}

