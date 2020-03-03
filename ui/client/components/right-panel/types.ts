 import {ProcessType, ProcessStateType} from "../Process/types"
import {NodeId} from "../../actions/nk/models"

export type ProcessPropertiesState = {
  id: NodeId,
  isSubprocess: boolean,

}

export type ProcessToDisplayState = {
  properties?: ProcessPropertiesState,
}

type NodeToDisplay = $TodoType

type SelectionState = string[]

type HistoryAction = $TodoType

type History = {
  past: HistoryAction[],
  future: HistoryAction[],
}

export type GraphState = {
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: ProcessToDisplayState,
  businessView: boolean,
  nodeToDisplay: NodeToDisplay,
  selectionState?: SelectionState,
  groupingState?: $TodoType,
  history: History,
  layout?: $TodoType,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  processCounts: $TodoType,
}
