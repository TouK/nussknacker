import {Action} from "../actions/reduxTypes"
import User from "../common/models/User"
import {ValidationContext} from "../actions/nk/nodeDetails";

export type NodeDetailsState = {

    parameters?: Map<string, ValidationContext>

}

const initialState: NodeDetailsState = {
}

export function reducer(state: NodeDetailsState = initialState, action: Action): NodeDetailsState {
  console.log("BLAAA", action)

  switch (action.type) {
    case "NODE_VALIDATION_UPDATED": {
      const {validationData} = action
      return {
        ...state,
        parameters: validationData.parameters,
      }
    }
    default:
      return state
  }
}
