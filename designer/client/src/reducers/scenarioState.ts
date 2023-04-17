import {Action, Reducer} from "../actions/reduxTypes"
import {ProcessStateType} from "../components/Process/types";

export interface ScenarioStateState {
  processState: ProcessStateType,
  processStateLoaded: boolean,
}


const initialState: ScenarioStateState = {
  processState: null,
  processStateLoaded: false,
}

export const reducer: Reducer<ScenarioStateState> = (state = initialState, action: Action): ScenarioStateState => {
  switch (action.type) {
    case "PROCESS_STATE_LOADED": {
      return {
        ...state,
        processState: action.processState,
        processStateLoaded: true,
      }
    }
    default:
      return state
  }
}
