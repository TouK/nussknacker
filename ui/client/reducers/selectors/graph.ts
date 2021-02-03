import {RootState} from "../index"
import {createSelector} from "reselect"
import ProcessStateUtils from "../../components/Process/ProcessStateUtils"
import ProcessUtils from "../../common/ProcessUtils"
import {GraphState, ProcessCounts} from "../graph"
import {Process} from "../../types"

export const getGraph = (state: RootState): GraphState => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessToDisplay = createSelector(getGraph, g => g.processToDisplay || {} as Process)
export const getProcessId = createSelector(getFetchedProcessDetails, d => d?.name)
export const getProcessVersionId = createSelector(getFetchedProcessDetails, d => d?.processVersionId)
export const getProcessCategory = createSelector(getFetchedProcessDetails, d => d?.processCategory || "")
export const getIsArchived = createSelector(getFetchedProcessDetails, d => d?.isArchived)
export const isStateLoaded = createSelector(getGraph, d => d?.processStateLoaded)
export const getProcessState = createSelector(getGraph, d => d?.processState)
export const isLatestProcessVersion = createSelector(getFetchedProcessDetails, d => d?.isLatestVersion)
export const isSubprocess = createSelector(getProcessToDisplay, p => p.properties?.isSubprocess)
export const isArchived = createSelector(getFetchedProcessDetails, p => p.isArchived)
export const isBusinessView = createSelector(getGraph, g => g.businessView)
export const isPristine = (state: RootState): boolean => ProcessUtils.nothingToSave(state)
export const hasError = (state: RootState): boolean => !ProcessUtils.hasNoErrors(getProcessToDisplay(state))
export const getNodeToDisplay = createSelector(getGraph, g => g.nodeToDisplay)
export const getSelectionState = createSelector(getGraph, g => g.selectionState)
export const getGroupingState = createSelector(getGraph, g => g.groupingState)
export const getHistory = createSelector(getGraph, g => g.history)

export const getFetchedProcessState = createSelector(
  getFetchedProcessDetails,
  isStateLoaded,
  getProcessState,
  (fetchedProcessDetails, isStateLoaded, processState) => isStateLoaded ? processState : fetchedProcessDetails?.state,
)

export const isSaveDisabled = createSelector(
  [isPristine, isLatestProcessVersion, isBusinessView],
  (pristine, latest, businessView) => pristine && latest || businessView,
)
export const isDeployPossible = createSelector(
  [isSaveDisabled, hasError, getFetchedProcessState, isSubprocess],
  (saveDisabled, error, state, subprocess) => !subprocess && saveDisabled && !error && ProcessStateUtils.canDeploy(state),
)
export const isMigrationPossible = createSelector(
  [isSaveDisabled, hasError, getFetchedProcessState],
  (saveDisabled, error, state) => saveDisabled && !error && ProcessStateUtils.canDeploy(state),
)
export const isCancelPossible = createSelector(getFetchedProcessState, state => ProcessStateUtils.canCancel(state))
export const isArchivePossible = createSelector(getFetchedProcessState, state => ProcessStateUtils.canArchive(state))
export const getTestCapabilities = createSelector(getGraph, g => g.testCapabilities || {})
const getTestResults = createSelector(getGraph, g => g.testResults)
export const getProcessCounts = createSelector(getGraph, g => g.processCounts || {} as ProcessCounts)
export const getShowRunProcessDetails = createSelector(
  [getTestResults, getProcessCounts],
  (testResults, processCounts) => testResults || processCounts,
)

export const hasOneVersion = createSelector(getFetchedProcessDetails, details => (details?.history || []).length <= 1)
export const getAdditionalFields = createSelector(getProcessToDisplay, p => p.properties?.additionalFields)

export const getEdgeToDisplay = createSelector(getGraph, state => state.edgeToDisplay)
export const getLayout = createSelector(getGraph, state => state.layout || [])
export const getNodeId = createSelector(getNodeToDisplay, node => node.id)
