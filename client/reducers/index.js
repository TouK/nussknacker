import { combineReducers } from 'redux';
import _ from 'lodash'

import GraphUtils from '../components/graph/GraphUtils'
import NodeUtils from '../components/graph/NodeUtils'

import UndoRedoReducer from '../undoredo/UndoRedoReducer'

function settingsReducer(state = {loggedUser: {}, featuresSettings: {}}, action) {
  switch (action.type) {
    case "LOGGED_USER": {
      return {
        ...state,
          loggedUser: action.user
      }
    }
    case "FEATURES_SETTINGS": {
      return {
        ...state,
        featuresSettings: action.featuresSettings
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
  modalDialog: {},
  expandedGroups: []
}
function uiStateReducer(state = emptyUiState, action) {
  const withAllModalsClosed = (newState) => {
    const allModalsClosed = !(newState.modalDialog.openDialog || newState.showNodeDetailsModal || newState.showEdgeDetailsModal || newState.confirmDialog.isOpen)
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
    case "TOGGLE_MODAL_DIALOG": {
      return withAllModalsClosed({
        ...state,
        modalDialog: {
          openDialog: action.openDialog
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
  processCounts: {},
  testResults: {}
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
      const stateAfterNodeRename = updateAfterNodeIdChange(state, action.before.id, action.after.id);
      const processToDisplay = GraphUtils.mapProcessWithNewNode(stateAfterNodeRename.processToDisplay, action.before, action.after)
      return {
        ...stateAfterNodeRename,
        processToDisplay: {
          ...processToDisplay,
          validationResult: action.validationResult
        },
        nodeToDisplay: action.after,
      }
    }
    case "DELETE_NODE": {
      var idToDelete = action.id
      const stateAfterNodeDelete = updateAfterNodeDelete(state, idToDelete)
      const processToDisplay = GraphUtils.deleteNode(stateAfterNodeDelete.processToDisplay, idToDelete);
      return {
        ...stateAfterNodeDelete,
        processToDisplay: processToDisplay,
        nodeToDisplay: processToDisplay.properties,
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
    case "DISPLAY_PROCESS_COUNTS": {
      return {
        ...state,
        processCounts: action.processCounts
      }
    }
    case "DISPLAY_TEST_RESULTS_DETAILS": {
      return {
        ...state,
        testResults: action.testResults,
        graphLoading: false
      }
    }
    case "HIDE_RUN_PROCESS_DETAILS": {
      return {
        ...state,
        testResults: null,
        processCounts: null
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
      const withUpdatedGroups = state.groupingState.length > 1 ?
        {
          ...state,
          processToDisplay: NodeUtils.createGroup(state.processToDisplay, state.groupingState),
          layout: []
        } :  state;
      return _.omit(withUpdatedGroups, 'groupingState')
    }
    case "CANCEL_GROUPING": {
      return _.omit(state, 'groupingState')
    }
    case "UNGROUP": {
      return {
        processToDisplay: NodeUtils.ungroup(state.processToDisplay, action.groupToRemove),
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
      return {
        ...state,
        processToDisplay: NodeUtils.editGroup(state.processToDisplay, action.oldGroupId, action.newGroup),
        nodeToDisplay: action.newGroup,
        layout: updateLayoutAfterNodeIdChange(state.layout, action.oldGroupId, action.newGroup.id)
      }
    }
    default:
      return state
  }


  function canGroup(state, newNode) {
    const newNodeId = newNode.id
    const currentGrouping = state.groupingState
    return !NodeUtils.nodeIsGroup(newNode) && currentGrouping.length == 0 ||
      currentGrouping.find(nodeId => state.processToDisplay.edges.find(edge => edge.from == nodeId && edge.to == newNodeId ||  edge.to == nodeId && edge.from == newNodeId))
  }

  function updateAfterNodeIdChange(state, oldId, newId) {
    const newLayout = updateLayoutAfterNodeIdChange(state.layout, oldId, newId);
    const withGroupsUpdated = NodeUtils.updateGroupsAfterNodeIdChange(state.processToDisplay, oldId, newId);
    return {
      ...state,
      processToDisplay: withGroupsUpdated,
      layout: newLayout
    }
  }

  function updateLayoutAfterNodeIdChange(layout, oldId, newId) {
    return _.map(layout, (n) => {
      if (oldId === n.id) {
        return {
          ...n,
          id: newId
        }
      } else return n;
    });
  }

  function updateAfterNodeDelete(state, idToDelete) {
    const layoutWithoutNode = _.filter(state.layout, (n) => n.id !== idToDelete);
    const withGroupsUpdated = NodeUtils.updateGroupsAfterNodeDelete(state.processToDisplay, idToDelete);
    return {
      ...state,
      processToDisplay: withGroupsUpdated,
      layout: layoutWithoutNode
    }
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
