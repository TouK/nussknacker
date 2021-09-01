import {Layout} from "../../actions/nk"
import {ProcessStateType, ProcessType} from "../../components/Process/types"
import {NodeType, Process} from "../../types"

export type ProcessCounts = Record<string, {
  errors: number,
  all: number,
  subprocessCounts?: $TodoType,
}>

export type GraphState = {
  graphLoading: boolean,
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: Process,
  nodeToDisplay?: NodeType,
  selectionState?: string[],
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: $TodoType,
  processCounts: ProcessCounts,
  unsavedNewName: string | null,
}
