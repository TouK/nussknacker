/* eslint-disable i18next/no-literal-string */
import {isEqual, pick, sortBy} from "lodash"
import undoable, {combineFilters, excludeAction} from "redux-undo"
import {Reducer} from "../../actions/reduxTypes"
import {GraphState} from "./types"
import {batchGroupBy} from "./batchGroupBy"

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
  graphLoading: false,
  processToDisplay: null,
  fetchedProcessDetails: null,
  testCapabilities: {},
  selectionState: [],
  processCounts: {},
  testResults: null,
  unsavedNewName: null,
}

const graphReducer: Reducer<GraphState> = (state = emptyGraphState, action) => {
  switch (action.type) {
    case "PROCESS_FETCH":
    case "PROCESS_LOADING": {
      return {
        ...state,
        graphLoading: true,
      }
    }
    case "UPDATE_TEST_CAPABILITIES": {
      return {
        ...state,
        testCapabilities: action.capabilities,
      }
    }
    case "LOADING_FAILED": {
      return {
        ...state,
        graphLoading: false,
      }
    }
    case "CLEAR_PROCESS": {
      return {
        ...state,
        processToDisplay: null,
        fetchedProcessDetails: null,
        testResults: null,
      }
    }
    case "URL_CHANGED": {
      return {
        ...state,
        ...emptyGraphState,
      }
    }
    case "HIDE_RUN_PROCESS_DETAILS": {
      return {
        ...state,
        testResults: null,
        processCounts: null,
      }
    }
    default:
      return state
  }
}

const undoableReducer = undoable(graphReducer, {
  ignoreInitialState: true,
  undoType: "UNDO",
  redoType: "REDO",
  clearHistoryType: ["CLEAR", "PROCESS_FETCH"],
  groupBy: batchGroupBy.init(),
  filter: combineFilters(
    excludeAction([
      "USER_TRACKING",
      "VALIDATION_RESULT",
      "DISPLAY_PROCESS",
      "UPDATE_IMPORTED_PROCESS",
      "PROCESS_STATE_LOADED",
      "UPDATE_BACKEND_NOTIFICATIONS",
    ]),
    (action, nextState, prevState) => {
      const keys: Array<keyof GraphState> = [
        "fetchedProcessDetails",
        "processToDisplay",
        "unsavedNewName",
        "selectionState",
      ]
      return !isEqual(
        pick(nextState, keys),
        pick(prevState._latestUnfiltered, keys),
      )
    },
  ),
})

//TODO: replace this with use of selectors everywhere
export function reducerWithUndo(state, action) {
  const history = undoableReducer(state?.history, action)
  return {...history.present, history}
}
