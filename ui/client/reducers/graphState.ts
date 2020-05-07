import {Layout} from "../actions/nk"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {NodeType, GroupId} from "../types"

type GraphHistoryAction = $TodoType

type GraphHistory = {
  past: GraphHistoryAction[],
  future: GraphHistoryAction[],
}

export type ProcessToDisplayState = {
  properties?: NodeType,
  nodes: NodeType[],
}

export type GroupingState = $TodoType[]

export type GraphState = {
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: ProcessToDisplayState,
  businessView: boolean,
  nodeToDisplay: NodeType,
  selectionState?: string[],
  groupingState?: GroupingState,
  history: GraphHistory,
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  edgeToDisplay: $TodoType,
  processCounts: $TodoType,
}
