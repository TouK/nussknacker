import {Action} from "../actions/reduxTypes"
import {NodeValidationError} from "../types"

export type GenericActionState = {
  validationErrors: NodeValidationError[],
  validationPerformed: boolean,
}

const initialState: GenericActionState = {
  validationErrors: [],
  validationPerformed: false,
}

export function reducer(state: GenericActionState = initialState, action: Action): GenericActionState {
  switch (action.type) {
    case "GENERIC_ACTION_VALIDATION_UPDATED": {
      const {validationData} = action
      return {
        validationErrors: validationData.validationErrors,
        validationPerformed: validationData.validationPerformed,
      }
    }
    default:
      return state
  }
}
