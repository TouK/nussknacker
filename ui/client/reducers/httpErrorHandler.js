const initialState = {
  error: null,
}

export function reducer(state = initialState, action) {
  switch (action.type) {
    case "HANDLE_HTTP_ERROR": {
      return {
        ...state,
        error: action.error,
      }
    }
    case "HANDLE_OUATHERROR": {
      return {
        ...state,
        error: action.error,
      }
    }
    default:
      return state
  }
}
