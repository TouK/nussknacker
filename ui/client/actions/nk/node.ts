import HttpService from "../../http/HttpService"
import {NodeType, NodeId, ProcessDefinitionData, ValidationResult} from "../../types"
import {Action, ThunkAction} from "../reduxTypes"
import {RootState} from "../../reducers"
import {Position, layoutChanged} from "./ui/layout"
import {EditEdgeAction} from "./editEdge"
import {EditNodeAction, RenameProcessAction} from "./editNode"
import {getProcessDefinitionData} from "../../reducers/selectors/settings"

//TODO: identify
type Edges = $TodoType[]
type EdgeType = $TodoType

export type NodesWithPositions = { node: NodeType, position: Position }[]

type DeleteNodesAction = {
  type: "DELETE_NODES",
  ids: NodeId[],
}

type NodesConnectedAction = {
  type: "NODES_CONNECTED",
  fromNode: NodeType,
  toNode: NodeType,
  processDefinitionData: ProcessDefinitionData,
  edgeType?: EdgeType,
}

type NodesDisonnectedAction = {
  type: "NODES_DISCONNECTED",
  from: NodeId,
  to: NodeId,
}

type NodesWithEdgesAddedAction = {
  type: "NODES_WITH_EDGES_ADDED",
  nodesWithPositions: NodesWithPositions,
  edges: Edges,
  processDefinitionData: ProcessDefinitionData,
}

type ValidationResultAction = {
  type: "VALIDATION_RESULT",
  validationResult: ValidationResult,
}

type DisplayNodeDetailsAction = {
  type: "DISPLAY_NODE_DETAILS",
  nodeToDisplay: NodeType,
}

type NodeAddedAction = {
  type: "NODE_ADDED",
  node: NodeType,
  position: Position,
}

//this WON'T work for async actions - have to handle promises separately
function runSyncActionsThenValidate<S extends RootState>(syncActions: (state: S) => Action[]): ThunkAction<void, S> {
  return (dispatch, getState) => {
    syncActions(getState()).forEach(action => dispatch(action))
    return HttpService.validateProcess(getState().graphReducer.processToDisplay).then(
      (response) => dispatch({type: "VALIDATION_RESULT", validationResult: response.data}),
    )
  }
}

export function displayNodeDetails(node: NodeType): DisplayNodeDetailsAction {
  return {
    type: "DISPLAY_NODE_DETAILS",
    nodeToDisplay: node,
  }
}

export function deleteNodes(ids: NodeId[]): ThunkAction {
  return runSyncActionsThenValidate(() => [{
    type: "DELETE_NODES",
    ids: ids,
  }])
}

export function nodesConnected(fromNode: NodeType, toNode: NodeType): ThunkAction {
  return runSyncActionsThenValidate(state => [
    {
      type: "NODES_CONNECTED",
      fromNode: fromNode,
      toNode: toNode,
      processDefinitionData: state.settings.processDefinitionData,
    },
  ])
}

export function nodesDisconnected(from: NodeId, to: NodeId): ThunkAction {
  return runSyncActionsThenValidate(() => [{
    type: "NODES_DISCONNECTED",
    from: from,
    to: to,
  }])
}

export function injectNode(from: NodeType, middle: NodeType, to: NodeType, edgeType: EdgeType): ThunkAction {
  return runSyncActionsThenValidate(state => [
    {
      type: "NODES_DISCONNECTED",
      from: from.id,
      to: to.id,
    },
    {
      type: "NODES_CONNECTED",
      fromNode: from,
      toNode: middle,
      processDefinitionData: state.settings.processDefinitionData,
      edgeType: edgeType,
    },
    {
      type: "NODES_CONNECTED",
      fromNode: middle,
      toNode: to,
      processDefinitionData: state.settings.processDefinitionData,
    },
  ])
}

export function nodeAdded(node: NodeType, position: Position): ThunkAction {
  return dispatch => {
    dispatch({type: "NODE_ADDED", node, position})
    dispatch(layoutChanged())
  }
}

export function nodesWithEdgesAdded(nodesWithPositions: NodesWithPositions, edges: Edges): ThunkAction {
  return (dispatch, getState) => {
    dispatch({
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions,
      edges,
      processDefinitionData: getProcessDefinitionData(getState()),
    })
    dispatch(layoutChanged())
  }
}

export type NodeActions =
  | DisplayNodeDetailsAction
  | NodeAddedAction
  | DeleteNodesAction
  | NodesConnectedAction
  | NodesDisonnectedAction
  | NodesWithEdgesAddedAction
  | ValidationResultAction
  | EditNodeAction
  | EditEdgeAction
  | RenameProcessAction
