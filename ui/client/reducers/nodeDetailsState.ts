import {Action} from "../actions/reduxTypes"
import {NodeValidationError, TypingResult, UIParameter} from "../types"

export type NodeDetailsState = {
  parameters?: UIParameter[],
  expressionType?: TypingResult,
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
      const {validationData, nodeId} = action
      return {
        ...state,
        [nodeId]: {
          ...state[nodeId],
          validationErrors: validationData.validationErrors,
          parameters: validationData.parameters,
          expressionType: validationData.expressionType,
          validationPerformed: validationData.validationPerformed,
        },
      }
    }
    case "NODE_VALIDATION_CLEAR": {
      const {nodeId} = action
      return {
        ...state,
        [nodeId]: initialState,
      }

    }
    //TODO: do we need to react on other actions?
    case "CLOSE_MODALS":
      return initialState
    default:
      return state
  }
}
