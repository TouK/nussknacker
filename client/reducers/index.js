import { combineReducers } from 'redux';
import _ from 'lodash'
import fp from 'lodash/fp'

import GraphUtils from '../components/graph/GraphUtils'
import NodeUtils from '../components/graph/NodeUtils'

import UndoRedoReducer from '../undoredo/UndoRedoReducer'

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

const emptyGraphState = {
  graphLoading: false,
  processToDisplay: {},
  fetchedProcessDetails: {},
  nodeToDisplay: {},
  layout: {},
  groupingState: null
}

function graphReducer(state, action) {
  switch (action.type) {
    case "PROCESS_LOADING": {
      return {
        ...state,
        graphLoading: true
      }
    }
    case "UPDATE_IMPORTED_PROCESS": {
      const oldNodeIds = _.sortBy(state.processToDisplay.nodes.map((n) => n.id))
      const newNodeids = _.sortBy(action.processJson.nodes.map((n) => n.id))
      const newLayout = _.isEqual(oldNodeIds, newNodeids) ? state.layout : null

      return {
        ...state,
        graphLoading: false,
        processToDisplay: action.processJson,
        layout: newLayout
      }
    }
    case "DISPLAY_PROCESS": {
      return {
        ...state,
        processToDisplay: action.fetchedProcessDetails.json,
        fetchedProcessDetails: action.fetchedProcessDetails,
        graphLoading: false,
        nodeToDisplay: action.fetchedProcessDetails.json.properties,
        layout: {} //potrzebne np przy pokazywaniu historycznej wersji
      }
    }
    case "LOADING_FAILED": {
      return {
        ...state,
        graphLoading: false
      }
    }
    case "CLEAR_PROCESS": {
      return {
        ...state,
        processToDisplay: {},
        fetchedProcessDetails: {},
        testResults: null
      }
    }
    case "DISPLAY_MODAL_NODE_DETAILS":
    case "DISPLAY_NODE_DETAILS":
      if (state.groupingState) {
        const newNodeId = action.nodeToDisplay.id
        return {
           ...state,
          //FIXME: dodanie do grupy
          groupingState: canGroup(state, action.nodeToDisplay) ? _.concat(state.groupingState, newNodeId) : state.groupingState
        }
      } else {
        return {
          ...state,
          nodeToDisplay: action.nodeToDisplay
        }
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
        ...emptyGraphState,
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
      var newId = createUniqueNodeId(state.processToDisplay.nodes, state.processToDisplay.nodes.length)
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
    case "DISPLAY_TEST_RESULTS": {
      return {
        ...state,
        testResults: action.testResults,
        graphLoading: false
      }
    }
    case "HIDE_TEST_RESULTS": {
      return {
        ...state,
        testResults: null
      }
    }
    case "START_GROUPING": {
      return _.omit({
        ...state,
        groupingState: []
      }, 'nodeToDisplay')
    }
    case "FINISH_GROUPING": {
      const updatedGroups = state.groupingState.length > 1 ?
              update('processToDisplay.properties.additionalFields.groups', (groups) => _.concat((groups || []), [state.groupingState]), fp.set('layout', {}, state)) :  state
      return _.omit(updatedGroups, 'groupingState')
    }
    case "CANCEL_GROUPING": {
      return _.omit(state, 'groupingState')
    }
    case "UNGROUP": {
      const updatedGroups =  update('processToDisplay.properties.additionalFields.groups',
        (groups) => groups.filter(e => !_.isEqual(e, action.groupToRemove)), state)
      return {
        ...updatedGroups,
        layout: {},
        nodeToDisplay: {}
      }
    }
    default:
      return state
  }

  //TODO: no przeciez to juz powinno byc??
  function update(path, fun, object) {
    return fp.set(path, fun(_.get(object, path)), object)
  }

  function canGroup(state, newNode) {
    const newNodeId = newNode.id
    const currentGrouping = state.groupingState
    return !NodeUtils.nodeIsGroup(newNode) && currentGrouping.length == 0 ||
      currentGrouping.find(nodeId => state.processToDisplay.edges.find(edge => edge.from == nodeId && edge.to == newNodeId ||  edge.to == nodeId && edge.from == newNodeId))
  }

  function createUniqueNodeId(nodes, nodeCounter) {
    var newId = `node${nodeCounter}`
    return _.some(nodes, (n) => {return n.id == newId}) ? createUniqueNodeId(nodes, nodeCounter + 1) : newId
  }
}

const emptyProcessActivity = {
  comments: [],
  attachments: []
}

function processActivityReducer(state = emptyProcessActivity, action) {
  switch (action.type) {
    case "DISPLAY_PROCESS_ACTIVITY": {
      return {
        ...state,
        comments: action.comments,
        attachments: action.attachments
      }
    }
    default:
      return state
  }
}

const espUndoableConfig = {
  blacklist: ["CLEAR_PROCESS", "PROCESS_LOADING", "URL_CHANGED"]
}
const rootReducer = combineReducers({
  graphReducer: UndoRedoReducer.espUndoable(graphReducer, espUndoableConfig),
  settings: settingsReducer,
  ui: uiStateReducer,
  processActivity: processActivityReducer
});

export default rootReducer;
