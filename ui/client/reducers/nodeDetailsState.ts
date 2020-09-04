import {Action} from "../actions/reduxTypes"
import {ValidationData} from "../actions/nk"
import {NodeValidationError, UIParameter, UITypedExpression} from "../types"

export type NodeDetailsState = {
    parameters? : UIParameter[],
    typedExpressions?: UITypedExpression[],
    validationErrors: NodeValidationError[],
    validationPerformed: boolean,
}

const initialState: NodeDetailsState = {
  validationErrors: [],
  validationPerformed: false,
}

export function reducer(state: NodeDetailsState = initialState, action: Action): NodeDetailsState {
  switch (action.type) {
    case "NODE_VALIDATION_UPDATED": {
      const {validationData} = action
      return {
        ...state,
        validationErrors: validationData.validationErrors,
        parameters: validationData.parameters,
        typedExpressions: validationData.typedExpressions,
        validationPerformed: validationData.validationPerformed,
      }
    }
    //TODO: do we need to react on other actions?
    case "DISPLAY_MODAL_NODE_DETAILS":
    case "CLOSE_MODALS":
      return initialState
    default:
      return state
  }
}
