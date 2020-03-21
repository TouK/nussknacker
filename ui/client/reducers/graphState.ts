import {NodeType} from "../actions/nk/models"
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
  nodeToDisplay: $TodoType,
  selectionState?: string[],
  groupingState?: $TodoType,
  history: GraphHistory,
  layout?: $TodoType,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  processCounts: $TodoType,
  edgeToDisplay: $TodoType,
}
