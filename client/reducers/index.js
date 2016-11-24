import { combineReducers } from 'redux';
import _ from 'lodash'
import GraphUtils from '../components/graph/GraphUtils'

const emptyEspState = {
  graphLoading: false,
  processToDisplay: {},
  fetchedProcessDetails: {},
  nodeToDisplay: {}
}

function settingsReducer(state = {loggedUser: {}, grafanaSettings: {}, processDefinitionData: {}}, action) {
  switch (action.type) {
    case "LOGGED_USER": {
      return {
        ...state,
          loggedUser: action.user
      }
    }
    case "GRAFANA_SETTINGS": {
      return {
        ...state,
          grafanaSettings: action.grafanaSettings
      }
    }
    case "PROCESS_DEFINITION_DATA": {
      return {
        ...state,
          processDefinitionData: action.processDefinitionData
      }
    }
    default:
      return state
  }
}

const emptyUiState = {
  leftPanelIsOpened: false,
  showNodeDetailsModal: false,
  confirmDialog: {}
}
function uiStateReducer(state = emptyUiState, action) {
  switch (action.type) {
    case "TOGGLE_LEFT_PANEL": {
      return {
        ...state,
        leftPanelIsOpened: action.leftPanelIsOpened
      }
    }
    case "CLOSE_NODE_DETAILS": {
      return {
        ...state,
        showNodeDetailsModal: false
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS": {
      return {
        ...state,
        showNodeDetailsModal: true
      }
    }
    case "TOGGLE_CONFIRM_DIALOG": {
      return {
        ...state,
        confirmDialog: {
          isOpen: action.isOpen,
          text: action.text,
          onConfirmCallback: action.onConfirmCallback
        }
      }
    }
    default:
      return {
        ...state
      }
  }
}

function graphReducer(state, action) {
  switch (action.type) {
    case "FETCH_PROCESS_TO_DISPLAY": {
      return {
        ...state,
        graphLoading: true
      }
    }
    case "DISPLAY_PROCESS": {
      return {
        ...state,
        processToDisplay: action.fetchedProcessDetails.json,
        fetchedProcessDetails: action.fetchedProcessDetails,
        graphLoading: false,
        nodeToDisplay: action.fetchedProcessDetails.json.properties
      }
    }
    case "CLEAR_PROCESS": {
      return {
        ...state,
        processToDisplay: {},
        fetchedProcessDetails: {}
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS":
    case "DISPLAY_NODE_DETAILS":
      return {
        ...state,
        nodeToDisplay: action.nodeToDisplay
      }
    case "EDIT_NODE": {
      const processToDisplay = GraphUtils.mapProcessWithNewNode(state.processToDisplay, action.before, action.after)
      var newLayout = _.map(state.layout, (n) => {
        if (action.before.id == n.id) {
          return {
            ...n,
            id: action.after.id
          }
        } else return n;
      });
      return {
        ...state,
        processToDisplay: {
          ...processToDisplay,
          validationResult: action.validationResult
        },
        nodeToDisplay: action.after,
        layout: newLayout
      }
    }
    case "DELETE_NODE": {
      var idToDelete = action.id
      const processToDisplay = GraphUtils.deleteNode(state.processToDisplay, idToDelete);
      var layoutWithoutNode = _.filter(state.layout, (n) => n.id != idToDelete);
      return {
        ...state,
        processToDisplay: processToDisplay,
        nodeToDisplay: processToDisplay.properties,
        layout: layoutWithoutNode
      }
    }
    case "URL_CHANGED": {
      return {
        ...state,
        ...emptyEspState,
      }
    }
    case "NODES_CONNECTED": {
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          edges: _.concat(state.processToDisplay.edges, {from: action.from, to: action.to})
        }
      }
    }
    case "NODES_DISCONNECTED": {
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          edges: _.reject(state.processToDisplay.edges, (e) => e.from == action.from && e.to == action.to)
        }
      }
    }
    case "NODE_ADDED": {
      var newId = `node${state.processToDisplay.nodes.length}`
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          nodes: _.concat(state.processToDisplay.nodes, {
            ... action.node,
            id: newId
          })
        },
        layout: _.concat(state.layout, {id: newId, position: action.position})
      }
    }
    case "LAYOUT_CHANGED": {
      return {
        ...state,
        layout: action.layout
      }
    }
    default:
      return state
  }
}


function espUndoable (reducer, config) {
  const emptyHistory = { history: {past: [], future: []}}
  const blacklist = _.concat(["@@INIT"], config.blacklist)
  const espUndoableFun = (state = emptyHistory, action) => {
    if (_.includes(blacklist, action.type)) {
      return reducer(state, action)
    } else {
      switch (action.type) {
        case "JUMP_TO_STATE":
          switch (action.direction) {
            case "PAST": {
              const newPast = state.history.past.slice(0, action.index + 1)
              const futurePartFromPast = state.history.past.slice(action.index + 1)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                ...stateBasedOnPast,
                history: {
                  past: newPast,
                  future: _.concat(futurePartFromPast, state.history.future)
                }
              }
            }
            case "FUTURE": {
              const pastPartFromFuture = state.history.future.slice(0, action.index + 1)
              const newFuture = state.history.future.slice(action.index + 1)
              const newPast = _.concat(state.history.past, pastPartFromFuture)
              const stateBasedOnPast = _.reduce(_.concat({}, newPast), reducer)
              return {
                ...stateBasedOnPast,
                history: {
                  past: newPast,
                  future: newFuture
                }
              }
            }
          }
        case "UNDO":
          const nextIndex = state.history.past.length - 2
          return espUndoableFun(state, {
            type: "JUMP_TO_STATE",
            index: nextIndex < 0 ? 1 : nextIndex,
            direction: "PAST"
          })
        case "REDO":
          return espUndoableFun(state, {type: "JUMP_TO_STATE", index: 0, direction: "FUTURE"})
        case "CLEAR":
          return {
              ...state,
              ...emptyHistory
          }
        default: {
          const newState = reducer(state, action)
          return _.isEqual(newState, state) ? state : {
              ...newState,
              history: {
                ...state.history,
                past: _.concat(state.history.past, action),
                future: []
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
const rootReducer = combineReducers({
  graphReducer: espUndoable(graphReducer, espUndoableConfig),
  settings: settingsReducer,
  ui: uiStateReducer
});

export default rootReducer;
