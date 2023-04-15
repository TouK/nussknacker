import HttpService from "../../http/HttpService"
import {Edge, EdgeType, NodeId, NodeType, Process, ProcessDefinitionData, ValidationResult} from "../../types"
import {Action, ThunkAction, ThunkDispatch} from "../reduxTypes"
import {RootState} from "../../reducers"
import {layoutChanged, Position} from "./ui/layout"
import {EditNodeAction, RenameProcessAction} from "./editNode"
import {getProcessDefinitionData} from "../../reducers/selectors/settings"
import {debounce} from "lodash"
import {batchGroupBy} from "../../reducers/graph/batchGroupBy"

//TODO: identify
type Edges = $TodoType[]

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

type NodeAddedAction = {
  type: "NODE_ADDED",
  node: NodeType,
  position: Position,
}

const debouncedValidate = debounce(
  (dispatch: ThunkDispatch<RootState>, getState: () => RootState, callback?: () => void) => HttpService
    .validateProcess(getState().graphReducer.processToDisplay)
    .then(({data}) => dispatch({type: "VALIDATION_RESULT", validationResult: data}))
    .finally(callback),
  250
)

//this WON'T work for async actions - have to handle promises separately
function runSyncActionsThenValidate<S extends RootState>(syncActions: (state: S) => Action[]): ThunkAction<void, S> {
  return (dispatch, getState) => {
    batchGroupBy.startOrContinue()
    syncActions(getState()).forEach(action => dispatch(action))
    debouncedValidate(dispatch, getState, () => batchGroupBy.end())
  }
}

export function deleteNodes(ids: NodeId[]): ThunkAction {
  return runSyncActionsThenValidate(() => [{
    type: "DELETE_NODES",
    ids: ids,
  }])
}

export function nodesConnected(fromNode: NodeType, toNode: NodeType, edgeType?: EdgeType): ThunkAction {
  return runSyncActionsThenValidate(state => [
    {
      type: "NODES_CONNECTED",
      processDefinitionData: state.settings.processDefinitionData,
      fromNode,
      toNode,
      edgeType,
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

export function injectNode(from: NodeType, middle: NodeType, to: NodeType, edge: Edge): ThunkAction {
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
      edgeType: edge.edgeType,
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
  return (dispatch) => {
    batchGroupBy.startOrContinue()
    dispatch({type: "NODE_ADDED", node, position})
    dispatch(layoutChanged())
    batchGroupBy.end()
  }}

export function nodesWithEdgesAdded(nodesWithPositions: NodesWithPositions, edges: Edges): ThunkAction {
  return (dispatch, getState) => {
    batchGroupBy.startOrContinue()
    dispatch({
      type: "NODES_WITH_EDGES_ADDED",
      nodesWithPositions,
      edges,
      processDefinitionData: getProcessDefinitionData(getState()),
    })
    dispatch(layoutChanged())
    batchGroupBy.end()
  }}

export type NodeActions =
  | NodeAddedAction
  | DeleteNodesAction
  | NodesConnectedAction
  | NodesDisonnectedAction
  | NodesWithEdgesAddedAction
  | ValidationResultAction
  | EditNodeAction
  | RenameProcessAction
