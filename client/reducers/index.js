import { combineReducers } from 'redux';
import _ from 'lodash'
import fp from 'lodash/fp'

import GraphUtils from '../components/graph/GraphUtils'
import NodeUtils from '../components/graph/NodeUtils'

import UndoRedoReducer from '../undoredo/UndoRedoReducer'

function settingsReducer(state = {loggedUser: {}, grafanaSettings: {}, kibanaSettings: {}, processDefinitionData: {}}, action) {
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
    case "KIBANA_SETTINGS": {
      return {
        ...state,
        kibanaSettings: action.kibanaSettings
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
  showEdgeDetailsModal: false,
  confirmDialog: {},
  toggleSaveProcessDialog: {},
  expandedGroups: []
}
function uiStateReducer(state = emptyUiState, action) {
  const withAllModalsClosed = (newState) => {
    const allModalsClosed = !(newState.showNodeDetailsModal || newState.showEdgeDetailsModal || newState.confirmDialog.isOpen || newState.toggleSaveProcessDialog.isOpen)
    return { ...newState, allModalsClosed: allModalsClosed}
  }
  switch (action.type) {
    case "TOGGLE_LEFT_PANEL": {
      return withAllModalsClosed({
        ...state,
        leftPanelIsOpened: action.leftPanelIsOpened
      })
    }
    case "CLOSE_MODALS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: false,
        showEdgeDetailsModal: false
      })
    }
    case "DISPLAY_MODAL_NODE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showNodeDetailsModal: true
      })
    }
    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return withAllModalsClosed({
        ...state,
        showEdgeDetailsModal: true
      })
    }
    case "TOGGLE_CONFIRM_DIALOG": {
      return withAllModalsClosed({
        ...state,
        confirmDialog: {
          isOpen: action.isOpen,
          text: action.text,
          onConfirmCallback: action.onConfirmCallback
        }
      })
    }
    case "TOGGLE_SAVE_PROCESS_DIALOG": {
      return withAllModalsClosed({
        ...state,
        saveProcessDialog: {
          isOpen: action.isOpen
        }
      })
    }
    case "EXPAND_GROUP": {
      return {
        ...state,
        expandedGroups: _.concat(state.expandedGroups, [action.id])
      }
    }
    case "COLLAPSE_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.filter(g => g != action.id)
      }
    }
    case "EDIT_GROUP": {
      return {
        ...state,
        expandedGroups: state.expandedGroups.map(id => id == action.oldGroupId ? action.newGroup.id : id)
      }
    }
    default:
      return withAllModalsClosed(state)
  }
}

const emptyGraphState = {
  graphLoading: false,
  processToDisplay: {},
  fetchedProcessDetails: {},
  nodeToDisplay: {},
  edgeToDisplay: {},
  layout: [],
  testCapabilities: {},
  groupingState: null,
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
    case "UPDATE_TEST_CAPABILITIES": {
      return {
        ...state,
        testCapabilities: action.capabilities
      }
    }
    case "DISPLAY_PROCESS": {
      return {
        ...state,
        processToDisplay: action.fetchedProcessDetails.json,
        fetchedProcessDetails: action.fetchedProcessDetails,
        graphLoading: false,
        nodeToDisplay: action.fetchedProcessDetails.json.properties,
        layout: [] //potrzebne np przy pokazywaniu historycznej wersji
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

    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return {
        ...state,
        edgeToDisplay: action.edgeToDisplay
      }
    }

    case "EDIT_EDGE": {
      const processToDisplay = GraphUtils.mapProcessWithNewEdge(state.processToDisplay, action.before, action.after)
      return {
        ...state,
        processToDisplay: {
          ...processToDisplay,
          validationResult: action.validationResult
        },
        edgeToDisplay: action.after,
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
      const baseEdge = {from: action.fromNode.id, to: action.toNode.id}
      const edgeType = NodeUtils.edgeType(state.processToDisplay.edges, action.fromNode, action.edgeTypes)
      const edge = edgeType ? {...baseEdge, edgeType: edgeType} : baseEdge
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          edges: _.concat(state.processToDisplay.edges, edge)
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
    //TODO: jakos inaczej to obsluzyc??
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
      return {
        ...state,
        groupingState: [],
        nodeToDisplay: state.processToDisplay.properties
      }
    }
    case "FINISH_GROUPING": {
      const groupId = state.groupingState.join("-")
      const updatedGroups = state.groupingState.length > 1 ?
              update('processToDisplay.properties.additionalFields.groups',
                (groups) => _.concat((groups || []), [{id: groupId, nodes: state.groupingState}]), fp.set('layout', {}, state)) :  state
      return _.omit(updatedGroups, 'groupingState')
    }
    case "CANCEL_GROUPING": {
      return _.omit(state, 'groupingState')
    }
    case "UNGROUP": {
      const updatedGroups =  update('processToDisplay.properties.additionalFields.groups',
        (groups) => groups.filter(e => !_.isEqual(e.id, action.groupToRemove)), state)
      return {
        ...updatedGroups,
        layout: [],
        nodeToDisplay: state.processToDisplay.properties,
      }
    }
    case "EXPAND_GROUP":
    case "COLLAPSE_GROUP": {
      return {
        ...state,
        layout: []
      }
    }
    case "EDIT_GROUP": {
      const groupForState = {id: action.newGroup.id, nodes: action.newGroup.ids}
      return update('processToDisplay.properties.additionalFields.groups',
                (groups) => _.concat((groups.filter(g => g.id != action.oldGroupId)), [groupForState]), { ...state, nodeToDisplay: action.newGroup})
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
