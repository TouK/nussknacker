import {Layout} from "../../actions/nk"
import {ProcessStateType, ProcessType} from "../../components/Process/types"
import {Process} from "../../types"
import {TestResults} from "../../common/TestResultUtils"

export interface NodeCounts {
  errors?: number,
  all?: number,
  subprocessCounts?: ProcessCounts,
}

export type ProcessCounts = Record<string, NodeCounts>

export type GraphState = {
  graphLoading: boolean,
  processState: ProcessStateType,
  processStateLoaded: boolean,
  fetchedProcessDetails?: ProcessType,
  processToDisplay?: Process,
  selectionState?: string[],
  layout: Layout,
  testCapabilities?: $TodoType,
  testResults: TestResults,
  processCounts: ProcessCounts,
  unsavedNewName: string | null,
}
