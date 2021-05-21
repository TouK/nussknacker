import {Layout} from "../../actions/nk"
import {ProcessStateType, ProcessType} from "../../components/Process/types"
import {NodeType, Process, GroupType, NodeId, Edge} from "../../types"

type GraphHistoryAction = $TodoType

type GraphHistory = {
  past: GraphHistoryAction[],
  future: GraphHistoryAction[],
}

export type ProcessCounts = $TodoType

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
  history: GraphHistory,
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  edgeToDisplay: Edge,
  processCounts: ProcessCounts,
  unsavedNewName: string | null,
}
