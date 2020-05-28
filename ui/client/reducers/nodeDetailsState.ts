import {Action} from "../actions/reduxTypes"
import {ValidationContext, ValidationError} from "../actions/nk"

export type NodeDetailsState = {
  parameters?: Map<string, ValidationContext>,
  validationErrors?: ValidationError[],
  validationPerformed?: boolean,
}

const initialState: NodeDetailsState = {
}

export function reducer(state: NodeDetailsState = initialState, action: Action): NodeDetailsState {
  switch (action.type) {
    case "NODE_VALIDATION_UPDATED": {
      const {validationData} = action
      return {
        ...state,
        parameters: validationData.parameters,
        validationErrors: validationData.validationErrors,
        validationPerformed: validationData.validationPerformed,
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS":
      return initialState
    default:
      return state
  }
}
