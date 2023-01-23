/* eslint-disable i18next/no-literal-string */
import {concat, isEqual, pick, sortBy, uniq, xor, zipObject} from "lodash"
import undoable, {combineFilters, excludeAction} from "redux-undo"
import {Reducer} from "../../actions/reduxTypes"
import * as GraphUtils from "../../components/graph/GraphUtils"
import * as LayoutUtils from "../layoutUtils"
import {nodes} from "../layoutUtils"
import {mergeReducers} from "../mergeReducers"
import {GraphState} from "./types"
import {
  addNodesWithLayout,
  adjustBranchParametersAfterDisconnect,
  createEdge,
  enrichNodeWithProcessDependentData,
  prepareNewNodesWithLayout,
  updateAfterNodeDelete,
  updateAfterNodeIdChange,
} from "./utils"
import {Edge, ValidationResult} from "../../types"
import NodeUtils from "../../components/graph/NodeUtils"
import {batchGroupBy} from "./batchGroupBy"

//TODO: We should change namespace from graphReducer to currentlyDisplayedProcess

const emptyGraphState: GraphState = {
  graphLoading: false,
  processToDisplay: null,
  fetchedProcessDetails: null,
  layout: [],
  testCapabilities: {},
  selectionState: [],
  processCounts: {},
  testResults: null,
  unsavedNewName: null,
}

export function updateValidationResult(state: GraphState, action: { validationResult: ValidationResult }): ValidationResult {
  return {
    ...action.validationResult,
    // nodeResults is sometimes empty although it shouldn't e.g. when SaveNotAllowed errors happen
    nodeResults: {
      ...state.processToDisplay.validationResult.nodeResults,
      ...action.validationResult.nodeResults,
    },
  }
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
        layout: LayoutUtils.fromMeta(processToDisplay),
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
    case "EDIT_NODE": {
      const stateAfterNodeRename = {
        ...state,
        ...updateAfterNodeIdChange(state.layout, action.processAfterChange, action.before.id, action.after.id),
      }
      return {
        ...stateAfterNodeRename,
        processToDisplay: {
          ...stateAfterNodeRename.processToDisplay,
          validationResult: updateValidationResult(state, action),
        },
      }
    }
    case "PROCESS_RENAME": {
      return {
        ...state,
        unsavedNewName: action.name,
      }
    }
    case "DELETE_NODES": {
      return action.ids.reduce((state, idToDelete) => {
        const stateAfterNodeDelete = updateAfterNodeDelete(state, idToDelete)
        const processToDisplay = GraphUtils.deleteNode(stateAfterNodeDelete.processToDisplay, idToDelete)
        return {
          ...stateAfterNodeDelete,
          processToDisplay,
        }
      }, state)
    }
    case "URL_CHANGED": {
      return {
        ...state,
        ...emptyGraphState,
      }
    }
    case "NODES_CONNECTED": {
      let newEdges: Edge[]

      const availableEdges = NodeUtils.edgesForNode(action.fromNode, action.processDefinitionData).edges
      const freeOutputEdges = state.processToDisplay.edges
        .filter(e => e.from === action.fromNode.id && !e.to)
        //we do this to skip e.g. edges that became incorrect/unavailable
        .filter(e => availableEdges.find(available => available?.name == e?.edgeType?.name && available?.type == e?.edgeType?.type))

      const freeOutputEdge = freeOutputEdges.find(e => e.edgeType === action.edgeType) || (freeOutputEdges.length == 0 ? null : freeOutputEdges[0])
      if (freeOutputEdge) {
        newEdges = state.processToDisplay.edges.map(e => e === freeOutputEdge ?
          {
            ...freeOutputEdge,
            to: action.toNode.id,
          } :
          e)
      } else {
        const edge = createEdge(action.fromNode, action.toNode, action.edgeType, state.processToDisplay.edges, action.processDefinitionData)
        newEdges = concat(state.processToDisplay.edges, edge)
      }

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
      const nodesToSet = adjustBranchParametersAfterDisconnect(state.processToDisplay.nodes, [action])
      return {
        ...state,
        processToDisplay: {
          ...state.processToDisplay,
          edges: state.processToDisplay.edges
            .map((e) => e.from === action.from && e.to === action.to ? {...e, to: ""} : e)
            .filter(Boolean),
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
      const edgesWithValidIds = action.edges.map(edge => ({
        ...edge,
        from: idToUniqueId[edge.from],
        to: idToUniqueId[edge.to],
      }))

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
          validationResult: updateValidationResult(state, action),
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
    },
  },
)

const undoableReducer = undoable(reducer, {
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
