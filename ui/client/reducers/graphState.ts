import {NodeType} from "../types"
import {ProcessStateType, ProcessType} from "../components/Process/types"

type GraphHistoryAction = $TodoType

type GraphHistory = {
  past: GraphHistoryAction[],
  future: GraphHistoryAction[],
}

export type ProcessToDisplayState = {
  properties?: NodeType,
}

export type GraphState = {
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: ProcessToDisplayState,
  businessView: boolean,
  nodeToDisplay: NodeType,
  selectionState?: string[],
  groupingState?: $TodoType,
  history: GraphHistory,
  layout?: $TodoType,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  processCounts: $TodoType,
  edgeToDisplay: $TodoType,
}
