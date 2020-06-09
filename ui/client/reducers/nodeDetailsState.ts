import {Action} from "../actions/reduxTypes"
import {ValidationData} from "../actions/nk"
import {NodeValidationError, UIParameter} from "../types"

export type NodeDetailsState = {
    parameters? : Map<string, UIParameter>,
    validationErrors: NodeValidationError[],
    validationPerformed: boolean,
    validationRequestPerformed: boolean,
}

const initialState: NodeDetailsState = {
  validationErrors: [],
  validationPerformed: false,
  validationRequestPerformed: false,
}

export function reducer(state: NodeDetailsState = initialState, action: Action): NodeDetailsState {
  switch (action.type) {
    case "NODE_VALIDATION_UPDATED": {
      const {validationData} = action
      return {
        ...state,
        validationErrors: validationData.validationErrors,
        parameters: validationData.parameters,
        validationPerformed: validationData.validationPerformed,
        validationRequestPerformed: true,
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS":
      return initialState
    default:
      return state
  }
}
