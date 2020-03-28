import {Layout} from "../actions/nk"
import {ProcessStateType, ProcessType} from "../components/Process/types"
import {NodeType} from "../types"

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
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  processCounts: $TodoType,
  edgeToDisplay: $TodoType,
}
