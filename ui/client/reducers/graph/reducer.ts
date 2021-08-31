import {concat, isEqual, pick, reject, sortBy, uniq, xor, zipObject} from "lodash"
import * as GraphUtils from "../../components/graph/GraphUtils"
import NodeUtils from "../../components/graph/NodeUtils"
import * as LayoutUtils from "../layoutUtils"
import {nodes} from "../layoutUtils"
import {mergeReducers} from "../mergeReducers"
import {reducer as groups} from "../groups"
import {Reducer} from "../../actions/reduxTypes"
import undoable, {combineFilters, excludeAction} from "redux-undo"
import {GraphState} from "./types"
import {
  displayOrGroup,
  updateLayoutAfterNodeIdChange,
  addNodesWithLayout,
  prepareNewNodesWithLayout,
  updateAfterNodeIdChange,
  updateAfterNodeDelete,
  canGroupSelection,
  createEdge,
  adjustBranchParametersAfterDisconnect,
  enrichNodeWithProcessDependentData,
} from "./utils"

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
  graphLoading: false,
  processToDisplay: null,
  fetchedProcessDetails: null,
  nodeToDisplay: null,
  edgeToDisplay: null,
  layout: [],
  testCapabilities: {},
  selectionState: [],
  processCounts: {},
  testResults: null,
  businessView: false,
  processState: null,
  processStateLoaded: false,
  unsavedNewName: null,
}

const graphReducer: Reducer<GraphState> = (state = emptyGraphState, action) => {
  switch (action.type) {
    case "PROCESS_LOADING": {
      return {
        ...state,
        graphLoading: true,
      }
    }
    case "PROCESS_STATE_LOADED": {
      return {
        ...state,
        processState: action.processState,
        processStateLoaded: true,
      }
    }
    case "UPDATE_IMPORTED_PROCESS": {
      const oldNodeIds = sortBy(state.processToDisplay.nodes.map((n) => n.id))
      const newNodeids = sortBy(action.processJson.nodes.map((n) => n.id))
      const newLayout = isEqual(oldNodeIds, newNodeids) ? state.layout : null

      return {
        ...state,
        graphLoading: false,
        processToDisplay: action.processJson,
        layout: newLayout,
      }
    }
    case "UPDATE_TEST_CAPABILITIES": {
      return {
        ...state,
        testCapabilities: action.capabilities,
      }
    }
    case "DISPLAY_PROCESS": {
      const {fetchedProcessDetails, businessView} = action
      const processToDisplay = fetchedProcessDetails.json
      return {
        ...state,
        processToDisplay,
        fetchedProcessDetails,
        businessView,
        graphLoading: false,
        nodeToDisplay: processToDisplay.properties,
        layout: !businessView ?
          LayoutUtils.fromMeta(processToDisplay):
          [],
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
        nodeToDisplay: null,
        testResults: null,
      }
    }

    case "DISPLAY_MODAL_NODE_DETAILS":
      return displayOrGroup(state, action.nodeToDisplay, action.nodeToDisplayReadonly)
    case "DISPLAY_NODE_DETAILS":
      return displayOrGroup(state, action.nodeToDisplay)

    case "DISPLAY_MODAL_EDGE_DETAILS": {
      return {
        ...state,
        edgeToDisplay: action.edgeToDisplay,
      }
    }

    case "EDIT_EDGE": {
      const processToDisplay = GraphUtils.mapProcessWithNewEdge(
        state.processToDisplay,
        action.before,
        action.after,
      )
      return {
        ...state,
        processToDisplay: {
          ...processToDisplay,
          validationResult: action.validationResult,
        },
        edgeToDisplay: action.after,
      }
    }
    case "EDIT_NODE": {
      const stateAfterNodeRename = {
        ...state,
        ...updateAfterNodeIdChange(state.layout, action.processAfterChange, action.before.id, action.after.id),
      }
      return {
        ...stateAfterNodeRename,
        processToDisplay: {
          ...stateAfterNodeRename.processToDisplay,
          validationResult: action.validationResult,
        },
        nodeToDisplay: action.after,
      }
    }
    case "PROCESS_RENAME": {
      return {
        ...state,
        unsavedNewName: action.name,
      }
    }
    case "DELETE_NODES": {
      const stateAfterDelete = action.ids.reduce((state, idToDelete) => {
        const stateAfterNodeDelete = updateAfterNodeDelete(state, idToDelete)
        const processToDisplay = GraphUtils.deleteNode(stateAfterNodeDelete.processToDisplay, idToDelete)
        return {
          ...stateAfterNodeDelete,
          processToDisplay: {
            ...processToDisplay,
          },
        }
      }, state)
      return {
        ...stateAfterDelete,
        nodeToDisplay: stateAfterDelete.processToDisplay.properties,
      }
    }
    case "URL_CHANGED": {
      return {
        ...state,
        ...emptyGraphState,
      }
    }
    case "NODES_CONNECTED": {
      const edge = createEdge(action.fromNode, action.toNode, action.edgeType, state.processToDisplay.edges, action.processDefinitionData)
      const newEdges = concat(state.processToDisplay.edges, edge)
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          nodes: state.processToDisplay.nodes.map(n => action.toNode.id !== n.id ?
            n :
            enrichNodeWithProcessDependentData(
              n,
              action.processDefinitionData,
              newEdges,
            )),
          edges: newEdges,
        },
      }
    }
    case "NODES_DISCONNECTED": {
      const nodesToSet = adjustBranchParametersAfterDisconnect(state.processToDisplay.nodes, action.from, action.to)
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          edges: reject(state.processToDisplay.edges, (e) => e.from === action.from && e.to === action.to),
          nodes: nodesToSet,
        },
      }
    }
    case "NODE_ADDED": {
      const nodeWithPosition = {
        node: action.node,
        position: action.position,
      }
      const {uniqueIds, nodes, layout} = prepareNewNodesWithLayout(state, [nodeWithPosition], false)
      return {
        ...addNodesWithLayout(state, {nodes, layout}),
        selectionState: uniqueIds,
      }
    }
    case "NODES_WITH_EDGES_ADDED": {
      const {nodes, layout, uniqueIds} = prepareNewNodesWithLayout(state, action.nodesWithPositions, true)

      const idToUniqueId = zipObject(action.nodesWithPositions.map(n => n.node.id), uniqueIds)
      const edgesWithValidIds = action.edges.map(edge => ({...edge, from: idToUniqueId[edge.from], to: idToUniqueId[edge.to]}))

      const updatedEdges = edgesWithValidIds.reduce((edges, edge) => {
        const fromNode = nodes.find(n => n.id === edge.from)
        const toNode = nodes.find(n => n.id === edge.to)
        const newEdge = createEdge(fromNode, toNode, edge.edgeType, edges, action.processDefinitionData)
        return edges.concat(newEdge)
      }, state.processToDisplay.edges)

      const stateWithNodesAdded = addNodesWithLayout(state, {nodes, layout})
      return {
        ...stateWithNodesAdded,
        processToDisplay: {
          ...stateWithNodesAdded.processToDisplay,
          edges: updatedEdges,
        },
        selectionState: uniqueIds,
      }
    }
    case "VALIDATION_RESULT": {
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          validationResult: action.validationResult,
        },
      }
    }
    //TODO: handle it differently?
    case "LAYOUT_CHANGED": {
      return {
        ...state,
        layout: action.layout,
      }
    }
    case "DISPLAY_PROCESS_COUNTS": {
      return {
        ...state,
        processCounts: action.processCounts,
      }
    }
    case "DISPLAY_TEST_RESULTS_DETAILS": {
      return {
        ...state,
        testResults: action.testResults,
        graphLoading: false,
      }
    }
    case "HIDE_RUN_PROCESS_DETAILS": {
      return {
        ...state,
        testResults: null,
        processCounts: null,
      }
    }
    case "EXPAND_GROUP":
    case "COLLAPSE_ALL_GROUPS":
    case "COLLAPSE_GROUP": {
      return {
        ...state,
        layout: [],
      }
    }
    case "EDIT_GROUP": {
      return {
        ...state,
        processToDisplay: {
          ...NodeUtils.editGroup(state.processToDisplay, action.oldGroupId, action.newGroup),
          validationResult: action.validationResult,
        },
        nodeToDisplay: action.newGroup,
        layout: updateLayoutAfterNodeIdChange(state.layout, action.oldGroupId, action.newGroup.id),
      }
    }
    case "EXPAND_SELECTION": {
      return {
        ...state,
        selectionState: uniq([...state.selectionState, ...action.nodeIds]),
      }
    }
    case "TOGGLE_SELECTION": {
      return {
        ...state,
        selectionState: xor(state.selectionState, action.nodeIds),
      }
    }
    case "RESET_SELECTION": {
      return {
        ...state,
        selectionState: action.nodeIds ? action.nodeIds : [],
      }
    }
    default:
      return state
  }
}

const reducer = mergeReducers(
  graphReducer,
  {
    processToDisplay: {
      nodes,
      properties: {
        additionalFields: {groups},
      },
    },
  },
)

const undoableReducer = undoable(reducer, {
  ignoreInitialState: true,
  undoType: "UNDO",
  redoType: "REDO",
  clearHistoryType: ["BUSINESS_VIEW_CHANGED", "CLEAR"],
  filter: combineFilters(
    excludeAction([
      "USER_TRACKING",
      //this actions triggers "LAYOUT_CHANGED" which is stored in history
      "EXPAND_GROUP", "COLLAPSE_GROUP",
    ]),
    (action, nextState, prevState) => {
      const keys = [
        "fetchedProcessDetails",
        "processToDisplay",
        "unsavedNewName",
        "layout",
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
