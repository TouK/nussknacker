import {createSelector} from "reselect"
import NodeUtils from "../../components/graph/NodeUtils"
import {Process} from "../../types"
import {RootState} from "../index"

export const getGraph = (state: RootState): RootState["graphReducer"] => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessToDisplay = createSelector(getGraph, g => g.processToDisplay || {} as Process)
export const getProcessNodesIds = createSelector(getProcessToDisplay, p => NodeUtils.nodesFromProcess(p).map(n => n.id))
export const getProcessId = createSelector(getFetchedProcessDetails, d => d?.name)
export const getProcessName = getProcessId
export const getProcessUnsavedNewName = createSelector(getGraph, (g) => g?.unsavedNewName)
export const getProcessCategory = createSelector(getFetchedProcessDetails, d => d?.processCategory || "")

export const isSubprocess = createSelector(getProcessToDisplay, p => p.properties?.isSubprocess)
export const isArchived = createSelector(getFetchedProcessDetails, p => p?.isArchived)

export const getTestResults = createSelector(getGraph, g => g.testResults)
