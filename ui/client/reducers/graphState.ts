import {Layout} from "../actions/nk"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {NodeType, Process, GroupType, NodeId} from "../types"

type GraphHistoryAction = $TodoType

type GraphHistory = {
  past: GraphHistoryAction[],
  future: GraphHistoryAction[],
}

export type GroupingState = NodeId[]

export type GraphState = {
  graphLoading: boolean,
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: Process,
  businessView: boolean,
  nodeToDisplay?: NodeType | GroupType,
  nodeToDisplayReadonly?: boolean,
  selectionState?: string[],
  groupingState?: GroupingState,
  history: GraphHistory,
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  edgeToDisplay: $TodoType,
  processCounts: $TodoType,
}
