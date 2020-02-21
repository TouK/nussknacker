import {RootState} from "../../reducers/index"
import ProcessUtils from "../../common/ProcessUtils"
import {createSelector} from "reselect"
import {SettingsState, GraphState} from "./types"

const getGraph = (state: RootState): GraphState => state.graphReducer
const getSettings = (state: RootState): SettingsState => state.settings

export const getFeatureSettings = createSelector(getSettings, s => s.featuresSettings)
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
export const getHistory = createSelector(getGraph, g => g.history)
