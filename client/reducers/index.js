import { combineReducers } from 'redux';
import _ from 'lodash'
import GraphUtils from '../components/graph/GraphUtils'
import * as ProcessToDisplayMode from '../constants/ProcessToDisplayMode'

const emptyEspState = {
  graphLoading: false,
  processToDisplay: {},
  fetchedProcessDetails: {},
  nodeToDisplay: {}
}

function espReducer(state = emptyEspState, action) {
  switch (action.type) {
    case "FETCH_PROCESS_TO_DISPLAY": {
      return {
        ...state,
        graphLoading: true
      }
    }
    case "DISPLAY_PROCESS": {
      let processToDisplay
      if (action.processToDisplayMode == ProcessToDisplayMode.CURRENT) {
        processToDisplay = action.fetchedProcessDetails.json
      } else if (action.processToDisplayMode == ProcessToDisplayMode.DEPLOYED) {
        processToDisplay = action.fetchedProcessDetails.deployedJson
      }
      return {
        ...state,
        processToDisplay: processToDisplay,
        fetchedProcessDetails: action.fetchedProcessDetails,
        graphLoading: false
      }
    }
    case "CLEAR_PROCESS": {
      return {
        ...state,
        processToDisplay: {},
        fetchedProcessDetails: {}
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
    case "EDIT_NODE": {
      const processToDisplay = GraphUtils.mapProcessWithNewNode(state.processToDisplay, action.before, action.after)
      return {
        ...state,
        processToDisplay: {
          ...processToDisplay,
          validationResult: action.validationResult
        },
        nodeToDisplay: action.after
      }
    }
    case "LOGGED_USER": {
      return {
        ...state,
        loggedUser: action.user
      }
    }
    case "URL_CHANGED": {
      return {
        ...state,
        ...emptyEspState
      }
    }
    default:
      return state
  }
}


function espUndoable (reducer, config) {
  const emptyHistory = { history: {past: [], future: []}}
  const blacklist = _.concat(["@@INIT"], config.blacklist)
  const espUndoableFun = (state = {espReducer: emptyHistory}, action) => {
    if (_.includes(blacklist, action.type)) {
      return reducer(state, action)
    } else {
      switch (action.type) {
        case "JUMP_TO_STATE":
          switch (action.direction) {
            case "PAST": {
              const newPast = state.espReducer.history.past.slice(0, action.index + 1)
              const futurePartFromPast = state.espReducer.history.past.slice(action.index + 1)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                espReducer: {
                  ...stateBasedOnPast.espReducer,
                  history: {
                    past: newPast,
                    future: _.concat(futurePartFromPast, state.espReducer.history.future)
                  }
                }
              }
            }
            case "FUTURE": {
              const pastPartFromFuture = state.espReducer.history.future.slice(0, action.index + 1)
              const newFuture = state.espReducer.history.future.slice(action.index + 1)
              const newPast = _.concat(state.espReducer.history.past, pastPartFromFuture)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                espReducer: {
                  ...stateBasedOnPast.espReducer,
                  history: {
                    past: newPast,
                    future: newFuture
                  }
                }
              }
            }
          }
        case "UNDO":
          const nextIndex = state.espReducer.history.past.length - 2
          return espUndoableFun(state, {
            type: "JUMP_TO_STATE",
            index: nextIndex < 0 ? 1 : nextIndex,
            direction: "PAST"
          })
        case "REDO":
          return espUndoableFun(state, {type: "JUMP_TO_STATE", index: 0, direction: "FUTURE"})
        case "CLEAR":
          return {
            espReducer: {
              ...state.espReducer,
              ...emptyHistory
            }
          }
        default: {
          const newState = reducer(state, action)
          return {
            //fixme czy musze tutaj odnosic sie do espReducer? jak trzymam historie tak po prostu to leca warningi? sprawdzic to
            espReducer: {
              ...newState.espReducer,
              history: {
                ...state.espReducer.history,
                past: _.concat(state.espReducer.history.past, action),
                future: []
              }
            }
          }
        }
      }
    }
  }
  return espUndoableFun
}

const espUndoableConfig = {
  blacklist: ["CLEAR_PROCESS", "FETCH_PROCESS_TO_DISPLAY", "URL_CHANGED"]
}
const rootReducer = espUndoable(combineReducers({
  espReducer
}), espUndoableConfig);

export default rootReducer;
