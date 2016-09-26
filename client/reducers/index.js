import { combineReducers } from 'redux';

export default function espReducer(state = {}, action) {
  switch (action.type) {
    case "DISPLAY_PROCESS": {
      return {
        ...state,
        processToDisplay: action.processToDisplay,
        processDetails: action.processDetails
      }
    }
    case "DISPLAY_NODE_DETAILS":
      return {
        ...state,
        nodeToDisplay: action.nodeToDisplay
      }
    case "CLOSE_NODE_DETAILS":
      return {
        ...state,
        nodeToDisplay: {}
      }
    case "NODE_CHANGE_PERSISTED":
      return {
        ...state,
        nodeToDisplay: action.after
      }

    default:
      return state
  }
}


const rootReducer = combineReducers({
  espReducer
});

export default rootReducer;
