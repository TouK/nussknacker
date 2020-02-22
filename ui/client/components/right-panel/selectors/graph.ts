import {RootState} from "../../../reducers/index"
import {GraphState} from "../types"
import {createSelector} from "reselect"
import ProcessStateUtils from "../../Process/ProcessStateUtils"
import {ProcessStateType} from "../../Process/types"
import ProcessUtils from "../../../common/ProcessUtils"

const getGraph = (state: RootState): GraphState => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessToDisplay = createSelector(getGraph, g => g.processToDisplay || {})
export const getProcessId = createSelector(getFetchedProcessDetails, d => d?.name)
export const getProcessVersionId = createSelector(getFetchedProcessDetails, d => d?.processVersionId)
export const isLatestProcessVersion = createSelector(getFetchedProcessDetails, d => d?.isLatestVersion)
export const isSubprocess = createSelector(getProcessToDisplay, p => p.properties?.isSubprocess)
export const isBusinessView = createSelector(getGraph, g => g.businessView)
export const isPristine = (state: RootState): boolean => ProcessUtils.nothingToSave(state)
export const hasError = (state: RootState): boolean => !ProcessUtils.hasNoErrors(getProcessToDisplay(state))
export const getNodeToDisplay = createSelector(getGraph, g => g.nodeToDisplay)
export const getSelectionState = createSelector(getGraph, g => g.selectionState)
export const getGroupingState = createSelector(getGraph, g => g.groupingState)
export const getHistory = createSelector(getGraph, g => g.history)

export function getFetchedProcessState(
  state: RootState,
  {processState, isStateLoaded}: { isStateLoaded: boolean, processState: ProcessStateType },
) {
  return isStateLoaded ? processState : getFetchedProcessDetails(state)?.state
}

export const isSaveDisabled = createSelector([isPristine, isLatestProcessVersion], (pristine, latest) => pristine && latest)
export const isDeployPossible = createSelector(
  [isSaveDisabled, hasError, getFetchedProcessState],
  (disabled, error, state) => disabled && !error && ProcessStateUtils.canDeploy(state),
)
export const isCancelPossible = createSelector(getFetchedProcessState, state => ProcessStateUtils.canCancel(state))
export const getTestCapabilities = createSelector(getGraph, g => g.testCapabilities || {})
const getTestResults = createSelector(getGraph, g => g.testResults)
const getProcessCounts = createSelector(getGraph, g => g.processCounts)
export const getShowRunProcessDetails = createSelector(
  [getTestResults, getProcessCounts],
  (testResults, processCounts) => testResults || processCounts,
)
export const isRunning = createSelector(getFetchedProcessState, state => ProcessStateUtils.isRunning(state))
export const hasOneVersion = createSelector(getFetchedProcessDetails, details => (details?.history || []).length <= 1)
