import {ProcessType} from "../../components/Process/types"
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
  fetchedProcessDetails?: ProcessType,
  selectionState?: string[],
  testCapabilities?: $TodoType,
  testResults: TestResults,
  processCounts: ProcessCounts,
  unsavedNewName: string | null,
}
