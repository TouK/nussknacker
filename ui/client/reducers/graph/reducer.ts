import {concat, sortBy, isEqual, reject, zipObject, omit, uniq, xor} from "lodash"
import * as GraphUtils from "../../components/graph/GraphUtils"
import NodeUtils from "../../components/graph/NodeUtils"
import * as LayoutUtils from "../layoutUtils"
import {nodes} from "../layoutUtils"
import {mergeReducers} from "../mergeReducers"
import {reducer as groups} from "../groups"
import {Reducer} from "../../actions/reduxTypes"
import {GraphState} from "./types"
import {
  displayOrGroup,
  updateLayoutAfterNodeIdChange,
  addNodesWithLayout,
  prepareNewNodesWithLayout,
  updateAfterNodeIdChange,
  updateAfterNodeDelete,
  removeSubprocessVersionForLastSubprocess,
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
  edgeToDisplay: {},
  layout: [],
  testCapabilities: {},
  groupingState: null,
  selectionState: [],
  processCounts: {},
  testResults: null,
  businessView: false,
  processState: null,
  processStateLoaded: false,
  history: {
    future: [],
    past: [],
  },
  unsavedNewName: null,
}

const STATE_PROPERTY_NAME = "groupingState"

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
      const {fetchedProcessDetails} = action
      const processToDisplay = fetchedProcessDetails.json
      return {
        ...state,
        processToDisplay,
        fetchedProcessDetails,
        graphLoading: false,
        nodeToDisplay: processToDisplay.properties,
        layout: !state.businessView ?
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
        const newSubprocessVersions = removeSubprocessVersionForLastSubprocess(stateAfterNodeDelete.processToDisplay, idToDelete)
        const processToDisplay = GraphUtils.deleteNode(stateAfterNodeDelete.processToDisplay, idToDelete)
        return {
          ...stateAfterNodeDelete,
          processToDisplay: {
            ...processToDisplay,
            properties: {
              ...processToDisplay.properties,
              subprocessVersions: newSubprocessVersions,
            },
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
      return addNodesWithLayout(
        state,
        prepareNewNodesWithLayout(state, [{
          node: action.node,
          position: action.position,
        }], false),
      )
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
    case "START_GROUPING": {
      return {
        ...state,
        groupingState: [],
        nodeToDisplay: state.processToDisplay.properties,
      }
    }
    case "FINISH_GROUPING": {
      const withUpdatedGroups = state.groupingState.length > 1 ?
        {
          ...state,
          processToDisplay: NodeUtils.createGroup(state.processToDisplay, state.groupingState),
          layout: [],
        } :
        state
      return omit(withUpdatedGroups, STATE_PROPERTY_NAME)
    }
    case "CANCEL_GROUPING": {
      return omit(state, STATE_PROPERTY_NAME)
    }
    case "UNGROUP": {
      return {
        ...state,
        processToDisplay: NodeUtils.ungroup(state.processToDisplay, action.groupToRemove),
        layout: [],
        nodeToDisplay: state.processToDisplay.properties,
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
    case "BUSINESS_VIEW_CHANGED": {
      return {
        ...state,
        businessView: action.businessView,
      }
    }
    default:
      return state
  }
}

export const reducer = mergeReducers(
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
