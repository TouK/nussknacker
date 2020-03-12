import {RootState} from "../index"
import {createSelector} from "reselect"
import {GraphState} from "../graphState"

const getGraph = (state: RootState): GraphState => state.graphReducer

export const getFetchedProcessDetails = createSelector(getGraph, g => g.fetchedProcessDetails)
export const getProcessToDisplay = createSelector(getGraph, g => g.processToDisplay || {})
export const getProcessCategory = createSelector(getFetchedProcessDetails, d => d?.processCategory || "")
export const isSubprocess = createSelector(getProcessToDisplay, p => p.properties?.isSubprocess)
