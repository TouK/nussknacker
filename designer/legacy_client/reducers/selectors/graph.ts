import {createSelector} from "reselect"
import {RootState} from "../index"

export const getGraph = (state: RootState): RootState["graphReducer"] => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessId = createSelector(getFetchedProcessDetails, d => d?.name)
export const getProcessUnsavedNewName = createSelector(getGraph, (g) => g?.unsavedNewName)
export const getProcessCategory = createSelector(getFetchedProcessDetails, d => d?.processCategory || "")

export const isArchived = createSelector(getFetchedProcessDetails, p => p?.isArchived)

