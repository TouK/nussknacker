import {Reducer} from "../actions/reduxTypes"

export interface ErrorType {
  response: { data: unknown, status: number },
}

export interface ErrorState {
  error: ErrorType,
}

const initialState: ErrorState = {
  error: null,
}

export const reducer: Reducer<ErrorState> = (state = initialState, action) => {
  switch (action.type) {
    case "HANDLE_HTTP_ERROR": {
      return {
        ...state,
        error: action.error,
      }
    }
    default:
      return state
  }
}
